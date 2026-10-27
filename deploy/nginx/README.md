# www.yinlinpai.com Nginx

The site file assumes the Spring Boot service listens on `127.0.0.1:8081` and the admin build is deployed to `/opt/yinzhou/admin-dist`.

Before enabling it on the server:

1. Copy the supplied certificate chain and private key to `/etc/nginx/ssl/yinlinpai/` as `www.yinlinpai.com.pem` and `www.yinlinpai.com.key`; set the key to owner `root:root`, mode `0600`.
2. Install `yinlinpai.conf` into `/etc/nginx/conf.d/`.
3. Run `nginx -t`, then reload Nginx only if validation succeeds.
4. Configure the DNS A record for `www.yinlinpai.com` to `182.92.185.57` and allow ports 80/443 in the cloud firewall.
5. Set `PUBLIC_BASE_URL=https://www.yinlinpai.com` for the backend service so new upload URLs use the canonical HTTPS host.

This file intentionally does not configure or remove `api.milsimnb.cn`; keep that existing virtual host running for the three-month compatibility period, measured from the actual cutover date.
