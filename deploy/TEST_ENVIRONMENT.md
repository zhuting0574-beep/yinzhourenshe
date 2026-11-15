# Test environment

The test deployment shares the production host and MySQL/Redis processes, but
uses separate application resources:

- Admin: `https://www.yinlinpai.com/test/`
- API base: `https://www.yinlinpai.com/test-api/api`
- Backend: `127.0.0.1:8082`
- Database: `wx_community_test`
- Database user: `wxapp_test` (test database privileges only)
- Redis database: `1`
- Runtime directory: `/opt/yinzhou-test`
- Upload directory: `/opt/yinzhou-test/uploads`
- Service: `yinzhou-test.service`
- WeChat URL-link target: `trial`

Build the test admin with its isolated base paths:

```bash
cd admin-web
VITE_API_BASE=/test-api/api npm run build -- --base=/test/
```

The mini-program release build continues to use the production API. Develop
and trial builds select the test API in `miniprogram/app.ts` and `app.js`.

Never configure the test service with the production database URL or user.
The test database user must not have privileges on `wx_community`.
