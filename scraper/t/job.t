#!/usr/bin/env perl

use strict;
use warnings;
use Test::More;

use Scraper::Job qw(build_job nil_uuid);

my $job = build_job(
  source_id    => 'confaz',
  title        => 'Convênio ICMS 42/2026',
  law_type     => 'convenio',
  source_url   => 'https://www.confaz.fazenda.gov.br/legislacao/convenios/convenio-42-2026.html',
  raw_content  => 'Texto integral da norma',
  published_at => '1 de abril de 2026',
  tags         => ['icms', 'confaz', 'icms'],
  state        => undef,
  ncm_scope    => [],
);

is($job->{company_id}, nil_uuid(), 'scraper jobs always target the shared nil UUID');
is($job->{law_ref}, 'CONVENIO ICMS 42/2026', 'law_ref is normalized for the ingestion worker');
is($job->{published_at}, '2026-04-01', 'published_at is normalized to ISO date');
is_deeply($job->{tags}, ['icms', 'confaz'], 'tags are de-duplicated while preserving order');
like($job->{request_id}, qr{\Ascraper-confaz-[0-9a-f-]{36}\z}, 'request_id includes source id and job UUID');
like($job->{created_at}, qr{\A\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z\z}, 'created_at is emitted as an ISO8601 UTC timestamp');

done_testing();
