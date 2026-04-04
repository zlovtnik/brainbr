use anyhow::Result;
use sqlx::{postgres::PgPoolOptions, PgPool};

pub async fn create_pool(database_url: &str, max_connections: u32) -> Result<PgPool> {
    let pool = PgPoolOptions::new()
        .max_connections(max_connections)
        .connect(database_url)
        .await?;
    Ok(pool)
}

pub async fn run_migrations(pool: &PgPool) -> Result<()> {
    sqlx::migrate!("./migrations").run(pool).await?;
    Ok(())
}

/// Sets the RLS session variables for the current transaction.
pub async fn set_tenant_session(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    company_id: uuid::Uuid,
) -> Result<()> {
    sqlx::query(
        "SELECT set_config('app.current_company_id', $1, TRUE), set_config('app.bypass_rls', 'false', TRUE)"
    )
    .bind(company_id.to_string())
    .fetch_one(&mut **tx)
    .await?;
    Ok(())
}
