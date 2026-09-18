#!/bin/sh
set -eu

# Zapuskat' tol'ko v izolirovannoj seti: Docker --network none ili unshare -n.
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
stage=$(mktemp -d)
pids=""
cleanup() {
    for pid in $pids; do kill "$pid" 2>/dev/null || true; done
    for pid in $pids; do wait "$pid" 2>/dev/null || true; done
    rm -rf "$stage"
}
trap cleanup EXIT HUP INT TERM
chmod 755 "$stage"
mkdir "$stage/conf.d"

for port in 18883 18885; do
    printf 'listener %s 127.0.0.1\nallow_anonymous true\npersistence false\n' "$port" > "$stage/$port.conf"
    mosquitto -c "$stage/$port.conf" > "$stage/$port.log" 2>&1 &
    pids="$pids $!"
done

# Menjaem tol'ko testovye adresy i TLS/autentifikaciju iz primerov.
sed -e 's@CHANGE_ME_LOCAL_MQTT_HOST:1883@127.0.0.1:18883@' \
    -e 's@growerhub.ru:8883@127.0.0.1:18885@' \
    -e 's@CHANGE_ME_GROWERHUB_USERNAME@smoke@g' \
    -e 's@CHANGE_ME_GROWERHUB_BASE_TOPIC@gh/z2m/smoke@g' \
    -e '/^remote_username /d' -e '/^remote_password /d' \
    -e '/^bridge_cafile /d' -e '/^bridge_insecure /d' \
    "$root/connector/mosquitto-bridge.conf.example" > "$stage/conf.d/bridge.conf"
sed "s@/mosquitto/config/conf.d@$stage/conf.d@" "$root/connector/mosquitto.conf" > "$stage/mosquitto.conf"

for port in 18883 18885; do
    attempt=0
    until mosquitto_pub -h 127.0.0.1 -p "$port" -t smoke/ready -m ready 2>/dev/null; do
        attempt=$((attempt + 1))
        if [ "$attempt" -ge 30 ]; then cat "$stage/$port.log"; exit 1; fi
        sleep 0.1
    done
done
mosquitto -c "$stage/mosquitto.conf" -v > "$stage/connector.log" 2>&1 &
pids="$pids $!"
mosquitto_pub -h 127.0.0.1 -p 18883 -t zigbee2mqtt/probe -m ready -q 1 -r
if ! received=$(mosquitto_sub -h 127.0.0.1 -p 18885 -t gh/z2m/smoke/probe -C 1 -W 10); then
    cat "$stage/connector.log"
    exit 1
fi
[ "$received" = ready ]

mosquitto_sub -h 127.0.0.1 -p 18883 -t '#' -v -W 4 > "$stage/local.messages" 2>/dev/null &
local_sub=$!
pids="$pids $local_sub"
mosquitto_sub -h 127.0.0.1 -p 18885 -t '#' -v -W 4 > "$stage/cloud.messages" 2>/dev/null &
cloud_sub=$!
pids="$pids $cloud_sub"
sleep 0.3

publish() { mosquitto_pub -h 127.0.0.1 -p "$1" -t "$2" -m "$3" -q 1; }
publish 18883 zigbee2mqtt/sensor '{"temperature":23.5}'
publish 18883 zigbee2mqtt/sensor/availability '{"state":"online"}'
publish 18883 zigbee2mqtt/bridge/devices '[]'
publish 18883 zigbee2mqtt/bridge/response/device/info '{"status":"ok"}'
publish 18885 gh/z2m/smoke/lamp/set '{"state":"ON"}'
publish 18885 gh/z2m/smoke/sensor/get '{"temperature":""}'
publish 18885 gh/z2m/smoke/bridge/request/device/info '{"id":"sensor"}'
publish 18883 zigbee2mqtt/bridge/config blocked-local-config
publish 18883 homeassistant/sensor/test/config blocked-ha-discovery
publish 18885 gh/z2m/smoke/sensor blocked-cloud-state
publish 18885 gh/z2m/other/lamp/set blocked-other-namespace
wait "$local_sub" || true
wait "$cloud_sub" || true

assert_once() {
    count=$(grep -Fxc "$2" "$1" || true)
    if [ "$count" -ne 1 ]; then
        printf 'Ozhidalos odno soobshchenie: %s; polucheno: %s\n' "$2" "$count"
        cat "$stage/connector.log"
        exit 1
    fi
}
assert_once "$stage/cloud.messages" 'gh/z2m/smoke/sensor {"temperature":23.5}'
assert_once "$stage/cloud.messages" 'gh/z2m/smoke/sensor/availability {"state":"online"}'
assert_once "$stage/cloud.messages" 'gh/z2m/smoke/bridge/devices []'
assert_once "$stage/cloud.messages" 'gh/z2m/smoke/bridge/response/device/info {"status":"ok"}'
assert_once "$stage/local.messages" 'zigbee2mqtt/lamp/set {"state":"ON"}'
assert_once "$stage/local.messages" 'zigbee2mqtt/sensor/get {"temperature":""}'
assert_once "$stage/local.messages" 'zigbee2mqtt/bridge/request/device/info {"id":"sensor"}'
assert_once "$stage/local.messages" 'zigbee2mqtt/sensor {"temperature":23.5}'
assert_once "$stage/cloud.messages" 'gh/z2m/smoke/lamp/set {"state":"ON"}'
if grep -Eq 'blocked-local-config|blocked-ha-discovery' "$stage/cloud.messages" \
    || grep -Eq 'blocked-cloud-state|blocked-other-namespace' "$stage/local.messages"; then
    printf 'Lishnie topiki proshli cherez connector\n'
    exit 1
fi
printf 'Connector: konfiguracija, retained state, telemetrija, komandy i izoljacija topikov proshli proverku.\n'
