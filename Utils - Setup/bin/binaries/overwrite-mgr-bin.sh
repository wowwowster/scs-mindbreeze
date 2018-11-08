#!/bin/sh

svcName=$1

sleep 8

cp -f scs/bin/newbin/* scs/bin
rm -Rf scs/bin/newbin

cp -f scs/bin/newroot/* .
rm -Rf scs/bin/newroot

chown $svcName.$svcName -R .
