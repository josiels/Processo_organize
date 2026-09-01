import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsHeaders } from '../_shared/cors.ts';

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing Authorization header' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;

    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userResult, error: userError } = await callerClient.auth.getUser();
    if (userError || !userResult.user) {
      return new Response(JSON.stringify({ error: 'Invalid session' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: perfilCaller, error: perfilError } = await adminClient
      .from('perfis')
      .select('papel, organizacao_id')
      .eq('id', userResult.user.id)
      .single();
    if (perfilError || perfilCaller?.papel !== 'admin' || !perfilCaller.organizacao_id) {
      return new Response(JSON.stringify({ error: 'Apenas admin pode criar contas' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const body = await req.json();
    const { nome, email, senha, papel } = body;
    if (!nome || !email || !senha || !['admin', 'usuario'].includes(papel)) {
      return new Response(JSON.stringify({ error: 'Campos obrigatórios ausentes ou papel inválido' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { data: novoUsuario, error: novoUsuarioError } = await adminClient.auth.admin.createUser({
      email,
      password: senha,
      email_confirm: true,
    });
    if (novoUsuarioError) {
      return new Response(JSON.stringify({ error: novoUsuarioError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const { error: perfilInsertError } = await adminClient.from('perfis').insert({
      id: novoUsuario.user.id,
      organizacao_id: perfilCaller.organizacao_id,
      papel,
      nome,
    });
    if (perfilInsertError) {
      // Compensating cleanup: an auth user with no perfil is a ghost account
      // that can log in but can't do anything.
      await adminClient.auth.admin.deleteUser(novoUsuario.user.id);
      return new Response(JSON.stringify({ error: perfilInsertError.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    return new Response(JSON.stringify({ perfil_id: novoUsuario.user.id }), {
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
