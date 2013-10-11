/bin/echo "postinstall script started [$1]"

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add exploud
else
  /sbin/chkconfig --list exploud
fi

mkdir -p /var/log/exploud

chown -R exploud:exploud /var/log/exploud

ln -s /var/log/exploud /usr/local/exploud/log

chown exploud:exploud /usr/local/exploud

/bin/echo "postinstall script finished"
exit 0
