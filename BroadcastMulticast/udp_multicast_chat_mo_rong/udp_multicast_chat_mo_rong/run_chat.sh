#!/usr/bin/env sh
printf "Nhap ten hien thi: "
read NAME
printf "Nhap local IPv4 (Enter de tu dong): "
read IP
if [ -z "$IP" ]; then
  java MulticastChat "$NAME"
else
  java MulticastChat "$NAME" "$IP"
fi
