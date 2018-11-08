#!/bin/sh

### BEGIN INIT INFO
# Provides:          SVC_NAME_PLACEHOLDER
# Required-Start:    $local_fs $remote_fs $network $syslog
# Required-Stop:     $local_fs $remote_fs $network $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Starts SVC_NAME_PLACEHOLDER at boot time
# Description:       Enables the Sword Connector Server (SCS)
### END INIT INFO

# chkconfig: 345 20 80

_start()
{
	echo "Starting Tomcat"
	su -s $(which sh) -c "$CATALINA_HOME/bin/startup.sh" SVC_NAME_PLACEHOLDER
}

_stop()
{
	echo "Stopping Tomcat"
	su -s $(which sh) -c "$CATALINA_HOME/bin/shutdown.sh" SVC_NAME_PLACEHOLDER
}

_isRunning()
{
	if [ -r "$CATALINA_HOME/work/tompid" ] 
	then 
		local PID=$(cat "$CATALINA_HOME/work/tompid")
		if ps -f -p $PID 2>&1 | grep org.apache.catalina.startup.Bootstrap > /dev/null; then return 0; else return 1; fi
	else 
		return 1
	fi
}

if [ -x /etc/rc.d/init.d/functions ] 
then 
	. /etc/rc.d/init.d/functions
fi

export JAVA_HOME="JDK_HOME_PLACEHOLDER"
export CATALINA_HOME="CATALINA_HOME_PLACEHOLDER"
export CATALINA_BASE="CATALINA_HOME_PLACEHOLDER"

case "$1" in
	start)
		if _isRunning 
		then 
			echo "Server is already running"
		else 
			_start
		fi
	;;
	stop)
		if _isRunning 
		then 
			_stop
		else 
			echo "Server is already stopped"
		fi
	;;
	restart)
		if _isRunning; then _stop; fi
		_start
	;;
	status)
		if _isRunning; then echo "Server is running"; else echo "Server is stopped"; fi
	;;
	*)
		echo "Usage: $0 {start|stop|status}"
		exit 1
	;;
esac
