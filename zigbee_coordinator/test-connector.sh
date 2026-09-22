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

for port in 18883 18885; do
    printf 'listener %s 127.0.0.1\nallow_anonymous true\npersistence false\n' "$port" > "$stage/$port.conf"
    mosquitto -c "$stage/$port.conf" > "$stage/$port.log" 2>&1 &
    pids="$pids $!"
done

for port in 18883 18885; do
    attempt=0
    until mosquitto_pub -h 127.0.0.1 -p "$port" -t smoke/ready -m ready 2>/dev/null; do
        attempt=$((attempt + 1))
        if [ "$attempt" -ge 30 ]; then cat "$stage/$port.log"; exit 1; fi
        sleep 0.1
    done
done
# Dva nezavisimyh connector ispolzujut odin lokalnyj broker i raznye Z2M temi.
for instance in smoke smoke-second; do
    local_topic=zigbee2mqtt
    connector_port=18884
    if [ "$instance" = smoke-second ]; then
        local_topic=zigbee2mqtt-second
        connector_port=18886
    fi
    mkdir -p "$stage/$instance/conf.d"
    sed -e 's@CHANGE_ME_LOCAL_MQTT_HOST:1883@127.0.0.1:18883@' \
        -e 's@growerhub.ru:8883@127.0.0.1:18885@' \
        -e "s@CHANGE_ME_GROWERHUB_USERNAME@$instance@g" \
        -e "s@CHANGE_ME_GROWERHUB_BASE_TOPIC@gh/z2m/$instance@g" \
        -e "s@zigbee2mqtt/@$local_topic/@g" \
        -e '/^remote_username /d' -e '/^remote_password /d' \
        -e '/^bridge_cafile /d' -e '/^bridge_insecure /d' \
        "$root/connector/mosquitto-bridge.conf.example" > "$stage/$instance/conf.d/bridge.conf"
    sed -e "s@/mosquitto/config/conf.d@$stage/$instance/conf.d@" \
        -e "s@listener 18884 @listener $connector_port @" \
        "$root/connector/mosquitto.conf" > "$stage/$instance/mosquitto.conf"
    mosquitto -c "$stage/$instance/mosquitto.conf" -v > "$stage/$instance/connector.log" 2>&1 &
    pids="$pids $!"
    mosquitto_pub -h 127.0.0.1 -p 18883 -t "$local_topic/probe" -m ready -q 1 -r
    if ! received=$(mosquitto_sub -h 127.0.0.1 -p 18885 -t "gh/z2m/$instance/probe" -C 1 -W 10); then
        cat "$stage/$instance/connector.log"
        exit 1
    fi
    [ "$received" = ready ]
done

if grep -q 'already connected' "$stage/18883.log"; then
    cat "$stage/18883.log"
    printf 'Connectory vytesnjajut drug druga po MQTT client ID.\n'
    exit 1
fi

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
publish 18883 zigbee2mqtt-second/sensor '{"temperature":19}'
publish 18885 gh/z2m/smoke-second/lamp/set '{"state":"OFF"}'
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
        cat "$stage"/*/connector.log
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
assert_once "$stage/cloud.messages" 'gh/z2m/smoke-second/sensor {"temperature":19}'
assert_once "$stage/local.messages" 'zigbee2mqtt-second/lamp/set {"state":"OFF"}'
if grep -Fqx 'gh/z2m/smoke/sensor {"temperature":19}' "$stage/cloud.messages" \
    || grep -Fqx 'gh/z2m/smoke-second/sensor {"temperature":23.5}' "$stage/cloud.messages" \
    || grep -Fqx 'zigbee2mqtt/lamp/set {"state":"OFF"}' "$stage/local.messages" \
    || grep -Fqx 'zigbee2mqtt-second/lamp/set {"state":"ON"}' "$stage/local.messages"; then
    printf 'Soobshchenie popalo v chuzhoe podkljuchenie.\n'
    exit 1
fi
if grep -Eq 'blocked-local-config|blocked-ha-discovery' "$stage/cloud.messages" \
    || grep -Eq 'blocked-cloud-state|blocked-other-namespace' "$stage/local.messages"; then
    printf 'Lishnie topiki proshli cherez connector\n'
    exit 1
fi
printf 'Dva connector: konfiguracija, retained state, telemetrija, komandy i izoljacija topikov proshli proverku.\n'
