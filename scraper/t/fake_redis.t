#!/usr/bin/env perl

use strict;
use warnings;
use Test::More;

use lib 't/lib';

use Test::FakeRedis;

my $redis = Test::FakeRedis->new;

$redis->set('lock', 'value', 'PX', 1500);
is($redis->ttl('lock'), 1, 'PX expiry is tracked in fake redis');

my $id = $redis->xadd('stream', '*', payload => '{"ok":true}');
like($id, qr/\A\d+-\d+\z/, 'xadd returns a generated stream entry id');
is($redis->{streams}{stream}[0]{id}, $id, 'stored xadd entry keeps the generated id');

done_testing();
