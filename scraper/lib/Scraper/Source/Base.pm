package Scraper::Source::Base;

use strict;
use warnings;
use v5.30;

use Carp qw(croak);
use File::Temp ();
use IPC::Open3 qw(open3);
use Mojo::JSON qw(decode_json);
use Mojo::URL;
use Symbol qw(gensym);
use Time::HiRes qw(sleep);

use Scraper::Normalise qw(clean_text extract_text_from_html);

sub new {
  my ($class, %args) = @_;

  croak 'ua is required' unless $args{ua};
  croak 'config is required' unless $args{config};

  return bless {
    ua     => $args{ua},
    config => $args{config},
    logger => $args{logger},
  }, $class;
}

sub ua     { return shift->{ua}; }
sub config { return shift->{config}; }

sub fetch_response {
  my ($self, $url, $request_id) = @_;

  my $retries = int($self->config->{max_retries} // 3);
  my $error;

  for my $attempt (1 .. $retries) {
    my $tx = eval { $self->ua->get($url) };

    if ($@) {
      $error = "Request failed before response: $@";
    }
    else {
      my $res = $tx->result;
      if ($res->is_success) {
        return {
          body         => $res->body,
          content_type => scalar($res->headers->content_type // q{}),
          final_url    => $tx->req->url->to_abs->to_string,
          status       => $res->code,
        };
      }

      my $http_error = $tx->error;
      my $code = $http_error->{code} // 0;
      my $message = $http_error->{message} // 'Unknown HTTP error';
      $error = "HTTP $code $message";

      if ($code >= 400 && $code < 500 && $code != 429) {
        $self->_log(
          warn => 'Skipping source fetch after client error',
          {
            request_id => $request_id,
            source_url => $url,
            attempt    => $attempt,
            status     => $code,
            error      => $message,
          },
        );
        return {
          skip   => 1,
          status => $code,
          error  => $message,
        };
      }
    }

    if ($attempt < $retries) {
      my $backoff = 5 * (2 ** ($attempt - 1));
      $self->_log(
        warn => 'Transient fetch failure, retrying',
        {
          request_id => $request_id,
          source_url => $url,
          attempt    => $attempt,
          backoff_s  => $backoff,
          error      => "$error",
        },
      );
      sleep($backoff);
      next;
    }
  }

  die {
    type       => 'fetch_failure',
    source_url => $url,
    request_id => $request_id,
    error      => "$error",
  };
}

sub fetch_html {
  my ($self, $url, $request_id) = @_;
  my $response = $self->fetch_response($url, $request_id);
  return $response if $response->{skip};
  $response->{html} = $response->{body};
  return $response;
}

sub fetch_json {
  my ($self, $url, $request_id) = @_;
  my $response = $self->fetch_response($url, $request_id);
  return $response if $response->{skip};
  $response->{json} = decode_json($response->{body});
  return $response;
}

sub fetch_text_document {
  my ($self, $url, $request_id) = @_;
  my $response = $self->fetch_response($url, $request_id);
  return $response if $response->{skip};

  my $content_type = $response->{content_type} // q{};
  my $is_pdf = $content_type =~ m{application/pdf}i || $url =~ /\.pdf(?:\?.*)?\z/i;

  $response->{text} = $is_pdf
    ? $self->_pdf_to_text($response->{body})
    : extract_text_from_html($response->{body});
  $response->{is_pdf} = $is_pdf ? 1 : 0;

  return $response;
}

sub absolute_url {
  my ($self, $base_url, $href) = @_;
  return Mojo::URL->new($href)->to_abs(Mojo::URL->new($base_url))->to_string;
}

sub discover_json_items {
  my ($self, $payload) = @_;

  return @{$payload} if ref $payload eq 'ARRAY';
  return unless ref $payload eq 'HASH';

  for my $key (qw(items results data content documentos publications)) {
    my $candidate = $payload->{$key};
    return @{$candidate} if ref $candidate eq 'ARRAY';
  }

  return;
}

sub derive_title_from_url {
  my ($self, $url) = @_;
  my $name = Mojo::URL->new($url)->path->parts->[-1] // q{};
  $name =~ s/\.[A-Za-z0-9]+\z//;
  $name =~ s/[-_]+/ /g;
  return clean_text($name);
}

sub _pdf_to_text {
  my ($self, $bytes) = @_;

  my $fh = File::Temp->new(SUFFIX => '.pdf', UNLINK => 1);
  my $filename = $fh->filename;
  binmode $fh;
  print {$fh} $bytes;
  close $fh;

  my ($stdout, $stderr) = (q{}, q{});
  my $timeout_s = int($self->config->{request_timeout_s} // 30);
  my $stderr_fh = File::Temp->new(UNLINK => 1);
  my $stdout_fh = gensym();
  my $pid;
  my $ok = eval {
    $pid = open3(undef, $stdout_fh, $stderr_fh, qw(pdftotext -layout -nopgbrk), $filename, '-');
    local $SIG{ALRM} = sub {
      if (defined $pid) {
        kill 'KILL', $pid;
        waitpid($pid, 0);
      }
      die "pdftotext timed out after ${timeout_s}s\n";
    };
    alarm $timeout_s;
    local $/;
    $stdout = <$stdout_fh> // q{};
    waitpid($pid, 0);
    alarm 0;
    1;
  };
  seek $stderr_fh, 0, 0;
  {
    local $/;
    $stderr = <$stderr_fh> // q{};
  }

  if (!$ok) {
    my $error = $@;
    alarm 0;
    chomp $error;
    die $error;
  }

  my $exit_status = $? >> 8;
  if ($exit_status != 0) {
    die "pdftotext failed: $stderr";
  }

  return clean_text($stdout);
}

sub _log {
  my ($self, $level, $message, $fields) = @_;
  if ($self->{logger}) {
    return $self->{logger}->($level, $message, $fields // {});
  }

  warn "$level $message\n";
  return;
}

1;
