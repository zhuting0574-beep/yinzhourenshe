# Community server

需要 Java 17、Maven 3.9、MySQL 8（Redis 可选）。先执行 `mysql -uroot -p < database/schema.sql`，再启动：

```bash
cd server
mvn spring-boot:run
```

环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`。主要接口：`GET /api/mini/activities`、`GET /api/mini/products`、`POST /api/mini/check-ins/{userId}`、`POST /api/mini/orders?userId=&productId=`。
