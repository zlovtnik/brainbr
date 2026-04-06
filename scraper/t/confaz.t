#!/usr/bin/env perl

use strict;
use warnings;
use autodie;
use Test::More;

use Mojo::UserAgent;

use Scraper::Source::Confaz;

our $INDEX_HTML = _slurp('fixtures/confaz/index.html');
our $DETAIL_HTML = _slurp('fixtures/confaz/convenio-42-2026.html');

{
  package Local::ConfazFixture;

  use parent 'Scraper::Source::Confaz';
  use Scraper::Normalise qw(extract_text_from_html);

  sub fetch_html {
    my ($self) = @_;
    return {
      html => $main::INDEX_HTML,
      body => $main::INDEX_HTML,
    };
  }

  sub fetch_text_document {
    my ($self, $url) = @_;
    return {
      body   => $main::DETAIL_HTML,
      text   => extract_text_from_html($main::DETAIL_HTML),
      is_pdf => 0,
      source_url => $url,
    };
  }
}

my $source = Local::ConfazFixture->new(
  ua => Mojo::UserAgent->new,
  config => {
    id            => 'confaz',
    index_url     => 'https://www.confaz.fazenda.gov.br/legislacao/convenios/',
    law_type      => 'convenio',
    request_limit => 10,
    tags          => ['icms', 'confaz'],
  },
);

my @links = $source->extract_index_links($INDEX_HTML, 'https://www.confaz.fazenda.gov.br/legislacao/convenios/');
is(scalar @links, 1, 'fixture index yields one convenio link');
is($links[0]{source_url}, 'https://www.confaz.fazenda.gov.br/legislacao/convenios/convenio-42-2026.html', 'relative source URL is expanded');

my @documents = $source->fetch_documents('scraper-confaz-run-test');
is(scalar @documents, 1, 'fixture fetch yields one parsed document');
is($documents[0]{law_ref}, 'CONVENIO ICMS 42/2026', 'parsed document derives a normalized law_ref');
is($documents[0]{published_at}, '2026-04-01', 'published date is extracted from the document body');
like($documents[0]{raw_content}, qr/estabelece procedimentos fiscais/i, 'raw content contains the extracted text');

done_testing();

sub _slurp {
  my ($relative) = @_;
  my $path = "t/$relative";
  open my $fh, '<', $path;
  local $/;
  return <$fh>;
}
