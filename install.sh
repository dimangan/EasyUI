#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C
umask 077

# Insert direct HTTPS download URLs for each file before running the installer.
# For a Git LFS JAR, use a binary download URL, not the raw LFS pointer.
readonly JAR_URL='https://github.com/dimangan/EasyUI/raw/refs/heads/master/build/libs/EasyUI.jar'
readonly PROPERTIES_URL='https://raw.githubusercontent.com/dimangan/EasyUI/master/src/main/resources/application.properties'
readonly NGINX_CONFIG_URL='https://raw.githubusercontent.com/dimangan/EasyUI/master/setup/nginx.config'
readonly INDEX_HTML_URL='https://raw.githubusercontent.com/dimangan/EasyUI/master/setup/index.html'

readonly app_dir=/etc/EasyUI
readonly service_user=easy-ui
readonly acme_root=/var/www/easy-ui-acme
readonly nginx_site=/etc/nginx/conf.d/easy-ui.conf
stage='Preflight'

log() {
    printf '[%(%Y-%m-%d %H:%M:%S %z)T] %s\n' -1 "$*" >&2
}

start_stage() {
    stage=$1
    log "$stage"
}

usage() {
    cat <<'EOF'
Usage: sudo bash install.sh DOMAIN

Supported systems: Debian 12 and Ubuntu 24.04 or 26.04 with systemd.
Point the domain to this server and allow inbound TCP ports 80 and 443.
Use an ASCII or Punycode domain name, without a scheme or port.
EOF
}

die() {
    log "ERROR [$stage] $*"
    exit 1
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    usage
    exit 0
fi
[[ $# == 1 ]] || { usage >&2; exit 2; }
domain=${1,,}
label='[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?'
[[ ${#domain} -le 253 && $domain =~ ^($label\.)+$label$ && $domain =~ [a-z] ]] ||
    die 'Specify a valid domain name.'
[[ $EUID == 0 ]] || die 'Run this installer with sudo.'

log_file=$(mktemp /var/log/easy-ui-install.XXXXXXXX.log)
exec > >(tee -a "$log_file") 2>&1
log "Installing EasyUI for $domain. Log file: $log_file"
trap 'status=$?; log "ERROR [$stage] Line $LINENO failed with exit status $status."' ERR

# shellcheck source=/dev/null
source /etc/os-release
case "$ID:$VERSION_ID" in
    debian:12|ubuntu:24.04|ubuntu:26.04) ;;
    *) die 'Use Debian 12, Ubuntu 24.04 or Ubuntu 26.04.' ;;
esac
[[ -d /run/systemd/system ]] || die 'Start systemd before running the installer.'
exec 9>/run/lock/easy-ui-install.lock
flock -n 9 || die 'Another EasyUI installation is running.'

work_dir=$(mktemp -d)
restore_nginx=0
cleanup() {
    if (( restore_nginx )); then
        log 'Restoring the previous Nginx configuration.'
        if [[ -f $work_dir/nginx.previous ]]; then
            install -m 0644 "$work_dir/nginx.previous" "$nginx_site"
        else
            rm -f -- "$nginx_site"
        fi
        nginx -t && systemctl reload nginx || true
    fi
    rm -rf -- "$work_dir"
}
trap cleanup EXIT

start_stage 'Installing system packages'
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl nginx certbot postgresql openjdk-17-jre-headless openssl ufw unzip
java_bin=$(dpkg-query -L openjdk-17-jre-headless | grep '/bin/java$')
[[ -x $java_bin ]] || die 'Java 17 executable was not found.'

https_get() {
    curl --fail --silent --show-error --location --retry 3 \
        --proto '=https' --proto-redir '=https' "$@"
}

download_file() {
    local download_url=$1 destination=$2
    local filename=${destination##*/}
    start_stage "GitHub: downloading $filename"
    https_get --output "$destination" "$download_url" ||
        die "The download of $filename failed (curl exit status $?)."
}

download_file "$JAR_URL" "$work_dir/EasyUI.jar"
start_stage 'Validating EasyUI.jar'
unzip -tq "$work_dir/EasyUI.jar"
unzip -p "$work_dir/EasyUI.jar" META-INF/MANIFEST.MF > "$work_dir/manifest"
grep -q '^Main-Class: ' "$work_dir/manifest" || die 'The downloaded JAR is not executable.'
if [[ ! -f $app_dir/application.properties ]]; then
    download_file "$PROPERTIES_URL" "$work_dir/application.properties"
    start_stage 'Validating application.properties'
    [[ -s $work_dir/application.properties ]] || die 'The downloaded properties file is empty.'
    if grep -Eiq '^[[:space:]]*<(html|!doctype)' "$work_dir/application.properties"; then
        die 'GitHub returned HTML instead of application.properties.'
    fi
fi
download_file "$NGINX_CONFIG_URL" "$work_dir/nginx.config"
start_stage 'Validating nginx.config'
for placeholder in '$fullchainpath' '$privkeypath' '$secretpath'; do
    grep -Fq -- "$placeholder" "$work_dir/nginx.config" ||
        die "The downloaded nginx.config is missing $placeholder."
done
download_file "$INDEX_HTML_URL" "$work_dir/index.html"
start_stage 'Validating index.html'
grep -Eiq '<(!doctype[[:space:]]+html|html[[:space:]>])' "$work_dir/index.html" ||
    die 'The downloaded index.html does not look like an HTML document.'

start_stage 'Preparing the service account and database credentials'
if ! id "$service_user" >/dev/null 2>&1; then
    useradd --system --user-group --home-dir "$app_dir" --shell /usr/sbin/nologin "$service_user"
fi
install -d -o root -g "$service_user" -m 0750 "$app_dir"

# Preserve credentials across retries and upgrades.
if [[ ! -f $app_dir/db.credentials ]]; then
    printf 'easy_ui_%s\n%s\n' "$(openssl rand -hex 6)" "$(openssl rand -hex 32)" > "$work_dir/db.credentials"
    install -m 0600 "$work_dir/db.credentials" "$app_dir/db.credentials"
fi
mapfile -t db_credentials < "$app_dir/db.credentials"
db_user=${db_credentials[0]:-}
db_password=${db_credentials[1]:-}
[[ $db_user =~ ^easy_ui_[0-9a-f]{12}$ && $db_password =~ ^[0-9a-f]{64}$ ]] ||
    die 'Invalid database credentials in /etc/EasyUI/db.credentials.'

start_stage 'Starting PostgreSQL and configuring the easy_ui database'
systemctl enable --now postgresql
postgres() {
    runuser -u postgres -- psql -X --dbname=postgres --set=ON_ERROR_STOP=1 "$@"
}
db_owner=$(postgres -Atc "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'easy_ui'")
[[ -z $db_owner || $db_owner == "$db_user" ]] ||
    die 'Database easy_ui already belongs to a different role.'
postgres <<SQL
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', '$db_user', '$db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$db_user')
\gexec
SELECT format('CREATE DATABASE easy_ui OWNER %I', '$db_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'easy_ui')
\gexec
SQL
PGPASSWORD="$db_password" psql -X -h 127.0.0.1 -U "$db_user" -d easy_ui \
    -Atc 'SELECT current_user' >/dev/null

start_stage 'Installing application configuration and setup templates'
# Log4j2 writes to logs/ relative to the service working directory.
install -d -o "$service_user" -g "$service_user" -m 0750 "$app_dir/logs"

if [[ ! -f $app_dir/application.properties ]]; then
    # Java properties use the final value when a key appears more than once.
    cat >> "$work_dir/application.properties" <<EOF


spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/easy_ui
spring.datasource.username=$db_user
spring.datasource.password=$db_password
EOF
    install -o root -g "$service_user" -m 0640 "$work_dir/application.properties" "$app_dir/"
fi

# DbInitializer reads -p= on startup. The JVM expands this file without
# putting the initial application password in the OS process arguments.
if [[ ! -f $app_dir/java.args ]]; then
    admin_password=$(openssl rand -hex 24)
    cat > "$work_dir/java.args" <<EOF
-jar
$app_dir/EasyUI.jar
--spring.config.location=file:$app_dir/application.properties
--server.address=127.0.0.1
--server.port=8080
--server.forward-headers-strategy=framework
--logging.level.ru.dimangan.easy_ui.utils.DbInitializer=WARN
-p=$admin_password
EOF
    install -o root -g "$service_user" -m 0640 "$work_dir/java.args" "$app_dir/"
fi

# The remote server setup feature opens these files relative to WorkingDirectory.
install -d -o root -g "$service_user" -m 0750 "$app_dir/setup"
install -o root -g "$service_user" -m 0640 \
    "$work_dir/nginx.config" "$work_dir/index.html" "$app_dir/setup/"

start_stage 'Allowing SSH, HTTP and HTTPS in UFW and enabling the firewall'
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

start_stage 'Preparing Nginx for HTTP certificate validation'
install -d -m 0755 "$acme_root" "$acme_root/.well-known" "$acme_root/.well-known/acme-challenge"
cert_name="easy-ui-$domain"
if (( ${#cert_name} > 245 )); then
    domain_hash=$(printf '%s' "$domain" | sha256sum)
    cert_name="easy-ui-${domain_hash%% *}"
fi
cert_dir="/etc/letsencrypt/live/$cert_name"
if [[ -f $nginx_site ]]; then
    cp -- "$nginx_site" "$work_dir/nginx.previous"
fi
restore_nginx=1

write_http_site() {
    cat <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $domain;

    location ^~ /.well-known/acme-challenge/ {
        root $acme_root;
    }

    location / {
        return 301 https://$domain\$request_uri;
    }
}
EOF
}

reload_nginx() {
    nginx -t
    systemctl enable --now nginx
    systemctl reload nginx
}

if [[ ! -s $cert_dir/fullchain.pem || ! -s $cert_dir/privkey.pem || ! -f $nginx_site ]] ||
    ! grep -Fxq "    server_name $domain;" "$nginx_site"; then
    write_http_site > "$nginx_site"
    reload_nginx
fi
start_stage "Requesting a TLS certificate for $domain"
certbot certonly --webroot -w "$acme_root" -d "$domain" --cert-name "$cert_name" \
    --non-interactive --agree-tos --register-unsafely-without-email --keep-until-expiring

start_stage 'Installing and starting easy-ui.service'
cat > /etc/systemd/system/easy-ui.service <<EOF
[Unit]
Description=EasyUI
Wants=network-online.target
Requires=postgresql.service
After=network-online.target postgresql.service

[Service]
Type=simple
User=$service_user
Group=$service_user
WorkingDirectory=$app_dir
ExecStart=$java_bin @$app_dir/java.args
Restart=on-failure
RestartSec=5s
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
install -o root -g "$service_user" -m 0640 "$work_dir/EasyUI.jar" "$app_dir/EasyUI.jar.new"
mv -f -- "$app_dir/EasyUI.jar.new" "$app_dir/EasyUI.jar"
systemctl daemon-reload
systemctl enable easy-ui.service
systemctl restart easy-ui.service

start_stage 'Waiting for EasyUI to respond on localhost:8080'
ready=0
for ((attempt = 0; attempt < 60; attempt++)); do
    if systemctl is-active --quiet easy-ui.service &&
        curl --fail --silent --noproxy '*' --max-time 2 --output /dev/null http://127.0.0.1:8080/; then
        ready=1
        break
    fi
    sleep 1
done
(( ready )) || die 'EasyUI did not start. Check journalctl -u easy-ui.service.'

start_stage 'Configuring HTTPS in Nginx'
{
    write_http_site
    cat <<EOF

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $domain;
    ssl_certificate $cert_dir/fullchain.pem;
    ssl_certificate_key $cert_dir/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
} > "$nginx_site"
reload_nginx
restore_nginx=0

start_stage 'Enabling automatic TLS certificate renewal'
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
cat > "$work_dir/reload-nginx" <<'EOF'
#!/bin/sh
set -e
case "$RENEWED_LINEAGE" in
    /etc/letsencrypt/live/easy-ui-*) nginx -t && systemctl reload nginx ;;
esac
EOF
install -m 0755 "$work_dir/reload-nginx" /etc/letsencrypt/renewal-hooks/deploy/easy-ui-nginx
systemctl enable --now certbot.timer

log "Installation completed. EasyUI is available at https://$domain/"
log 'Initial admin login: admin'
admin_password=$(sed -n 's/^-p=//p' "$app_dir/java.args")
if [[ -n $admin_password ]]; then
    log "Initial admin password: $admin_password"
else
    log 'No initial admin password found in java.args.'
fi
