package Scraper::Source::RfbInstrucoes;

use strict;
use warnings;
use v5.30;

use Mojo::DOM;
use parent 'Scraper::Source::Base';

use Scraper::Normalise qw(clean_text extract_first_date normalise_law_ref);

sub fetch_documents {
  my ($self, $request_id) = @_;

  my $index_url = $self->config->{index_url};
  my $index = $self->fetch_html($index_url, $request_id);
  return () if $index->{skip};

  my @links = $self->extract_index_links($index->{html}, $index_url);
  my $limit = $self->config->{request_limit};
  splice @links, $limit if defined $limit && $limit > 0 && @links > $limit;

  my @documents;
  for my $entry (@links) {
    my $document = $self->fetch_text_document($entry->{source_url}, $request_id);
    next if $document->{skip};

    my $raw_text = clean_text($document->{text});
    next unless length $raw_text;

    push @documents, {
      title        => $entry->{title},
      law_ref      => normalise_law_ref($entry->{title}),
      law_type     => $self->config->{law_type},
      source_url   => $entry->{source_url},
      raw_content  => $raw_text,
      published_at => extract_first_date($raw_text),
      effective_at => undef,
      tags         => [ @{$self->config->{tags} // []} ],
      state        => undef,
      ncm_scope    => [],
    };
  }

  return @documents;
}

sub extract_index_links {
  my ($self, $html, $base_url) = @_;

  my $dom = Mojo::DOM->new($html);
  my %seen;
  my @links;

  for my $anchor ($dom->find('a')->each) {
    my $href = $anchor->attr('href') // next;
    next if $href =~ /\A(?:mailto|javascript):/i;

    my $text = clean_text($anchor->all_text);
    next unless $href =~ /instr|normativa|consulta/i
      || $text =~ /instru(?:c|\x{00e7})(?:ao|\x{00e3}o|oes|\x{00f5}es).*normativa/i;

    my $absolute = eval { $self->absolute_url($base_url, $href) };
    next unless $absolute;
    next if $seen{$absolute}++;

    push @links, {
      source_url => $absolute,
      title      => length $text ? $text : $self->derive_title_from_url($absolute),
    };
  }

  return @links;
}

1;
