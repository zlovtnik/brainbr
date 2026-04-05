FROM rust:1.88-slim AS builder
WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*

COPY Cargo.toml ./
# Cache deps layer for both binaries
RUN mkdir -p src/bin \
    && echo "fn main(){}" > src/bin/api.rs \
    && echo "fn main(){}" > src/bin/worker.rs \
    && cargo build --release --bins 2>/dev/null || true \
    && rm -rf src

COPY src ./src
COPY migrations ./migrations
RUN cargo build --release --bin api --bin worker

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=builder /workspace/target/release/api /app/api
COPY --from=builder /workspace/target/release/worker /app/worker
COPY --from=builder /workspace/migrations /app/migrations
RUN chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["/app/api"]
