#!/bin/sh

checkNoPath()
{
	if type $1 >/dev/null 2>&1; then return 1; else return 0; fi
}

showPathCandidates()
{
	echo Command \"$1\" not found in PATH - please add one of the following directories to PATH and re-run installer
	find / -name "$1" -print0 | xargs -0 -r dirname
	if [ "$2" = "ex" ]; then exit 1; fi
}

getScriptFolderPath()
{
	local curDir="$(pwd)"
	local scriptPath="$0"
	if [ `expr index "$scriptPath" /` -ne 1 ]; then scriptPath="$curDir/$0"; fi
	local scriptDir=$(dirname "$scriptPath")
	cd "$scriptDir"
	scriptDir="$(pwd)"
	echo "$scriptDir"
	cd "$curDir"
}

if [ "$(id -u)" != "0" ]; then
	echo "The installation process must be run as root ; restarting with appropriate permissions"
	su -c "$0"
else

	if checkNoPath find 
	then 
		echo \"find\" command not found in PATH - please install \"find\" or update PATH variable so that find command can be found
		exit 1
	fi
	if checkNoPath sh; then showPathCandidates sh ex; fi
	if checkNoPath useradd; then showPathCandidates useradd ex; fi
	if checkNoPath usermod; then showPathCandidates usermod ex; fi
	if checkNoPath userdel; then showPathCandidates userdel ex; fi
	if checkNoPath groupdel; then showPathCandidates groupdel ex; fi
	
	scsRoot=$(getScriptFolderPath)
	cd "$scsRoot"
	
	if [ -e "./scs/bin/setup.jar" ] 
	then 
		setupJar="./scs/bin/setup.jar"
	elif [ -e "./gsp/bin/setup.jar" ] 
	then 
		setupJar="./gsp/bin/setup.jar"
	else 
		setupJar="./gsp/bin/GSP Manager.jar"
	fi
	
	if [ "$1" = "/NoGUI" ] 
	then 
		case "$2" in
			"SvcStart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"svcstart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"start")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"SvcStop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"svcstop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"stop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"SvcRestart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"svcrestart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"restart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"ApplyPatch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$3"
				;;
			"Patch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$3"
				;;
			"patch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$3"
				;;
			*)
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH"
				;;
		esac
	else 
		case "$1" in
			"SvcStart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"svcstart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"start")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStart
				;;
			"SvcStop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"svcstop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"stop")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcStop
				;;
			"SvcRestart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"svcrestart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"restart")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode SvcRestart
				;;
			"ApplyPatch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$2"
				;;
			"Patch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$2"
				;;
			"patch")
				./jdk/bin/java -jar "$setupJar" /NoGUI /EnvPath "$PATH" /Mode ApplyPatch "$2"
				;;
			*)
				./jdk/bin/java -jar "$setupJar" /EnvPath "$PATH"
				;;
		esac
	fi
	
fi
