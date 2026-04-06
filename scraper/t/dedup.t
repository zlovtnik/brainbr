#!/usr/bin/env perl

use strict;
use warnings;
use Test::More;

use lib 't/lib';

use Scraper::Dedup;
use Test::FakeRedis;

my $redis = Test::FakeRedis->new;
my $dedup = Scraper::Dedup->new(
  redis_db    => $redis,
  ttl_seconds => 123,
);

my $first = $dedup->inspect('confaz', 'CONVENIO ICMS 42/2026', 'texto original');
ok($first->{should_enqueue}, 'first occurrence should enqueue');
ok($first->{is_new}, 'first occurrence is marked new');
ok($dedup->commit('confaz', 'CONVENIO ICMS 42/2026', $first->{digest}, $first->{previous_digest}), 'first digest is committed');
is($redis->ttl('scraper:seen:confaz'), 123, 'set TTL is applied on commit');
is($redis->ttl('scraper:seen:confaz:law_ref'), 123, 'law_ref map TTL is applied on commit');

my $duplicate = $dedup->inspect('confaz', 'CONVENIO ICMS 42/2026', 'texto original');
ok(!$duplicate->{should_enqueue}, 'duplicate content is skipped');
ok(!$duplicate->{changed}, 'duplicate content is not treated as changed');

my $changed = $dedup->inspect('confaz', 'CONVENIO ICMS 42/2026', 'texto alterado');
ok($changed->{should_enqueue}, 'changed content should enqueue');
ok($changed->{changed}, 'changed content is detected');
ok(!$changed->{is_new}, 'changed content is not marked as a brand-new law_ref');
ok($dedup->commit('confaz', 'CONVENIO ICMS 42/2026', $changed->{digest}, $changed->{previous_digest}), 'changed digest is committed');

my @members = $redis->smembers('scraper:seen:confaz');
is(scalar @members, 1, 'only one active digest remains in the dedup set');
is($redis->hget('scraper:seen:confaz:law_ref', 'CONVENIO ICMS 42/2026'), $changed->{digest}, 'law_ref map tracks the latest digest');

done_testing();
