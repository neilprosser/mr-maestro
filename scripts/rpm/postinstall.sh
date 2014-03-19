/bin/echo "postinstall script started [$1]"

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add exploud
else
  /sbin/chkconfig --list exploud
fi

ln -s /var/encrypted/logs/exploud /var/log/exploud

chown -R exploud:exploud /usr/local/exploud

chmod 755 /usr/local/exploud/bin

/bin/echo "postinstall script finished"
exit 0
