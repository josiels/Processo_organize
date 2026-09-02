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

    const gatilhos: Array<{ rpc: string; montarMensagem: (item: ElegivelBase & Record<string, unknown>) => { titulo: string; corpo: string } }> = [
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
        if (tokensError || !tokensRows || tokensRows.length === 0) continue;

        const { titulo, corpo } = gatilho.montarMensagem(item);
        const tokens = tokensRows.map((r) => r.token_fcm as string);
        await enviarParaTokens(fcmAccessToken, projectId, tokens, titulo, corpo, item.processo_id);
        totalEnviado += tokens.length;
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
