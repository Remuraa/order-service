# Order Service

Microsserviço de pedidos - um projeto de estudos sobre microsserviços

## Stack

Java 25 · Spring Boot 4.1 · Maven · Spring Web MVC · Spring Data JPA · MySQL 8.4 · Flyway · Bean Validation · Lombok · Actuator

## Estrutura

```
src/main/java/com/estudos/orderservice
├── domain/        # Entidades JPA (Order, OrderItem) e regras de status (OrderStatus)
├── repository/    # Spring Data JPA
├── service/       # Casos de uso / transações
├── web/           # Controller REST e DTOs (records)
└── exception/     # Exceções de negócio e handler global (Problem Details, RFC 9457)
src/main/resources
├── application.yml
└── db/migration/  # Scripts Flyway (V1__..., V2__...)
```

## Como executar

**Pré-requisitos:** Docker. Para rodar fora do Docker, também JDK 25.

O serviço usa o MySQL compartilhado (container `mysql`, porta **3306**) com um database próprio, `order_db`. Esse MySQL precisa estar rodando antes.

Configure as variáveis copiando o modelo e preenchendo as credenciais do banco:

```bash
cp .env.example .env   # preencha DB_USERNAME e DB_PASSWORD
```

O `.env` é lido pela aplicação (`spring.config.import`) e pelo Docker Compose (`env_file`), e não vai para o git.

Serviço em container (entra na rede `mysql_default` do MySQL compartilhado):

```bash
docker compose --profile app up -d --build
```

Aplicação local (bom para desenvolver/debugar), conectando em `localhost:3306`:

```bash
./mvnw spring-boot:run     # ou: mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8081`. Documentação da API (Swagger UI): `http://localhost:8081/swagger-ui.html` - JSON do OpenAPI em `/v3/api-docs`.

Variáveis (no `.env` ou como variáveis de ambiente, que têm prioridade): `SERVER_PORT`, `SWAGGER_ENABLED`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

Testes:

```bash
mvn verify
```

## API

| Método | Rota                            | Descrição                                  |
|--------|---------------------------------|--------------------------------------------|
| POST   | `/api/v1/orders`                | Cria um pedido (status inicial `PENDING`)  |
| GET    | `/api/v1/orders`                | Lista paginada (`?status=&page=&size=&sort=`) |
| GET    | `/api/v1/orders/{id}`           | Busca um pedido pelo ID                    |
| PATCH  | `/api/v1/orders/{id}/status`    | Atualiza o status                          |
| GET    | `/actuator/health`              | Health check (`/liveness`, `/readiness`)   |

### Exemplos

```bash
# Criar
curl -i -X POST localhost:8081/api/v1/orders -H 'Content-Type: application/json' -d '{
  "customerId": 1,
  "items": [
    {"productId": 10, "quantity": 2, "unitPrice": 15.50},
    {"productId": 20, "quantity": 1, "unitPrice": 9.00}
  ]
}'

# Listar (filtrando por status)
curl 'localhost:8081/api/v1/orders?status=PENDING&page=0&size=10'

# Buscar por ID
curl localhost:8081/api/v1/orders/1

# Atualizar status
curl -X PATCH localhost:8081/api/v1/orders/1/status -H 'Content-Type: application/json' -d '{"status":"CONFIRMED"}'
```

### Ciclo de vida do status

```
PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED
   │            │
   └────────────┴──► CANCELLED
```

Transições fora desse fluxo retornam **422**. Pedido inexistente → **404**. Payload inválido → **400** com o campo `errors`. Alteração concorrente (lock otimista via `@Version`) → **409**.

## Próximos passos (roadmap de estudos)

- [ ] Publicar eventos `OrderCreated` / `OrderStatusChanged` no Kafka (padrão Outbox)
- [ ] Testes de integração com Testcontainers (MySQL e Kafka)
- [ ] Manifests Kubernetes (Deployment, Service, ConfigMap, Secret, probes)
- [ ] Novos serviços (ex.: Payment, Inventory) consumindo os eventos
