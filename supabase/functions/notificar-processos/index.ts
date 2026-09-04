import { createClient } from 'jsr:@supabase/supabase-js@2';
import { GoogleAuth } from 'npm:google-auth-library@9';
import { corsHeaders } from '../_shared/cors.ts';

interface ElegivelBase {
  perfil_id: string;
  processo_id: string;
  numero: string;
  objeto: string;
}

async function obterAccessTokenFcm(serviceAccountJson: string): Promise<string> {
  const credentials = JSON.parse(serviceAccountJson);
  const auth = new GoogleAuth({
    credentials,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  });
  const client = await auth.getClient();
  const { token } = await client.getAccessToken();
  if (!token) throw new Error('Não foi possível obter access token do FCM');
  return token;
}

async function enviarParaTokens(
  fcmAccessToken: string,
  projectId: string,
  tokens: string[],
  titulo: string,
  corpo: string,
  processoId: string,
) {
  for (const token of tokens) {
    try {
      await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${fcmAccessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token,
            notification: { title: titulo, body: corpo },
            data: { processo_id: processoId },
          },
        }),
      });
      // Falha de envio para um token específico (ex: token expirado) não deveria
      // derrubar o lote inteiro — cada chamada é independente; o FCM responde
      // por token, não há necessidade de checar o corpo da resposta aqui para
      // que o restante dos envios continue (fora de escopo tratar tokens
      // inválidos nesta etapa — ver spec, seção 6, "fora de escopo").
    } catch (err) {
      // Exceção de rede (DNS, conexão recusada, timeout) no fetch de UM token
      // não deve interromper o envio para os demais tokens/perfis/gatilhos —
      // registra e segue para o próximo token.
      console.error(`Falha de rede ao enviar FCM para token (processo ${processoId}):`, err);
    }
  }
}

Deno.serve(async (_req) => {
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const fcmServiceAccountJson = Deno.env.get('FCM_SERVICE_ACCOUNT_JSON');

    if (!fcmServiceAccountJson) {
      return new Response(JSON.stringify({ error: 'FCM_SERVICE_ACCOUNT_JSON não configurado — ver Plano 2D, seção 2' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);
    const projectId = JSON.parse(fcmServiceAccountJson).project_id as string;
    const fcmAccessToken = await obterAccessTokenFcm(fcmServiceAccountJson);

    let totalEnviado = 0;

    const gatilhos: Array<{
      rpc: string;
      // Só os 2 gatilhos novos (baseados em evento único de designacoes, não
      // em janela de tempo repetida como os 4 antigos) marcam notificado_em
      // depois de processar — evita reenviar a mesma designação/devolução a
      // cada ciclo do cron. Os 4 antigos ficam com o comportamento de sempre
      // (achado de spam já parqueado, fora do escopo deste ajuste).
      marcarNotificado?: boolean;
      montarMensagem: (item: ElegivelBase & Record<string, unknown>) => { titulo: string; corpo: string };
    }> = [
      {
        rpc: 'perfis_a_notificar_avanco_fase',
        montarMensagem: (item) => ({
          titulo: 'Processo avançou de fase',
          corpo: `O processo ${item.numero} (${item.objeto}) avançou de fase.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_prazo',
        montarMensagem: (item) => ({
          titulo: 'Prazo se aproximando',
          corpo: `O processo ${item.numero} (${item.objeto}) tem prazo em ${item.prazo_limite}.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_tempo_parado_fase',
        montarMensagem: (item) => ({
          titulo: 'Processo parado há muito tempo',
          corpo: `O processo ${item.numero} (${item.objeto}) está parado nesta fase há ${item.dias_parado} dias.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_tempo_desde_designado',
        montarMensagem: (item) => ({
          titulo: 'Processo designado há muito tempo',
          corpo: `O processo ${item.numero} (${item.objeto}) está com você há ${item.dias_designado} dias.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_designacao',
        marcarNotificado: true,
        montarMensagem: (item) => ({
          titulo: 'Você recebeu um processo',
          corpo: `O processo ${item.numero} (${item.objeto}) foi designado a você.`,
        }),
      },
      {
        rpc: 'perfis_a_notificar_devolucao',
        marcarNotificado: true,
        montarMensagem: (item) => ({
          titulo: 'Processo devolvido',
          corpo: `${item.devolvido_por_nome} devolveu o processo ${item.numero} (${item.objeto}). Motivo: ${item.motivo}.`,
        }),
      },
    ];

    for (const gatilho of gatilhos) {
      const { data: elegiveis, error } = await adminClient.rpc(gatilho.rpc);
      if (error) {
        console.error(`Erro ao consultar ${gatilho.rpc}:`, error.message);
        continue;
      }
      for (const item of (elegiveis ?? []) as Array<ElegivelBase & Record<string, unknown>>) {
        const { data: tokensRows, error: tokensError } = await adminClient
          .from('device_tokens')
          .select('token_fcm')
          .eq('perfil_id', item.perfil_id);

        if (!tokensError && tokensRows && tokensRows.length > 0) {
          const { titulo, corpo } = gatilho.montarMensagem(item);
          const tokens = tokensRows.map((r) => r.token_fcm as string);
          await enviarParaTokens(fcmAccessToken, projectId, tokens, titulo, corpo, item.processo_id);
          totalEnviado += tokens.length;
        }

        // Marca mesmo sem token (perfil nunca logou num build com Firebase
        // real): sem isto o evento ficaria elegível pra sempre, reprocessado
        // a cada ciclo do cron sem nunca poder ser entregue de verdade.
        if (gatilho.marcarNotificado && item.designacao_id) {
          await adminClient
            .from('designacoes')
            .update({ notificado_em: new Date().toISOString() })
            .eq('id', item.designacao_id as string);
        }
      }
    }

    return new Response(JSON.stringify({ enviados: totalEnviado }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
