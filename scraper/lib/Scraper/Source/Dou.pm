package Scraper::Source::Dou;

use strict;
use warnings;
use v5.30;

use Mojo::URL;
use parent 'Scraper::Source::Base';

use Scraper::Normalise qw(clean_text extract_first_date normalise_law_ref);

sub fetch_documents {
  my ($self, $request_id) = @_;

  my $url = Mojo::URL->new($self->config->{api_url});
  $url->query(%{$self->config->{query} // {}});

  my $response = $self->fetch_json($url->to_string, $request_id);
  return () if $response->{skip};

  my @items = $self->discover_json_items($response->{json});
  my @documents;

  ITEM:
  for my $item (@items) {
    next unless ref $item eq 'HASH';

    my $title = clean_text(
      $item->{title}
      // $item->{titulo}
      // $item->{headline}
      // $item->{identifica}
      // q{}
    );
    next unless length $title;

    my $source_url = $self->_item_url($item, $self->config->{api_url});
    my $raw_content = clean_text(
      $item->{content}
      // $item->{texto}
      // $item->{body}
      // $item->{ementa}
      // $item->{descricao}
      // q{}
    );

    if ((!length $raw_content || $raw_content eq $title) && defined $source_url) {
      my $detail = $self->fetch_text_document($source_url, $request_id);
      next ITEM if $detail->{skip};
      $raw_content = clean_text($detail->{text});
    }

    next unless length $raw_content;

    push @documents, {
      title        => $title,
      law_ref      => normalise_law_ref($title),
      law_type     => $self->config->{law_type},
      source_url   => $source_url,
      raw_content  => $raw_content,
      published_at => $self->_published_at($item, $raw_content),
      effective_at => undef,
      tags         => [ @{$self->config->{tags} // []} ],
      state        => undef,
      ncm_scope    => [],
    };

    my $limit = $self->config->{request_limit};
    last if defined $limit && $limit > 0 && @documents >= $limit;
  }

  return @documents;
}

sub _item_url {
  my ($self, $item, $base_url) = @_;

  for my $key (qw(url link href urlVisualizacao urlTexto detail_url)) {
    next unless defined $item->{$key} && length $item->{$key};
    return $self->absolute_url($base_url, $item->{$key});
  }

  return;
}

sub _published_at {
  my ($self, $item, $raw_content) = @_;

  for my $key (qw(published_at publicationDate dataPublicacao pubDate date createdAt)) {
    next unless defined $item->{$key} && length $item->{$key};
    return extract_first_date($item->{$key}) // $item->{$key};
  }

  return extract_first_date($raw_content);
}

1;
