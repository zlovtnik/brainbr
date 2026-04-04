FROM rust:1.82-slim AS builder
WORKDIR /workspace

RUN apt-get update && apt-get install -y pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*

COPY Cargo.toml ./
# Cache deps layer
RUN mkdir src && echo "fn main(){}" > src/main.rs && cargo build --release --bin api 2>/dev/null || true
RUN rm -rf src

COPY src ./src
COPY migrations ./migrations
RUN cargo build --release --bin api --bin worker

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app

COPY --from=builder /workspace/target/release/api /app/api
COPY --from=builder /workspace/target/release/worker /app/worker
COPY --from=builder /workspace/migrations /app/migrations

EXPOSE 8080
ENTRYPOINT ["/app/api"]
