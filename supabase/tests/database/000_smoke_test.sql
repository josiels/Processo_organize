begin;
select plan(1);
select ok(1 = 1, 'pgTAP test harness is wired up');
select coalesce(string_agg(f, E'\n'), 'ALL TESTS PASSED') as summary from finish() f;
rollback;
