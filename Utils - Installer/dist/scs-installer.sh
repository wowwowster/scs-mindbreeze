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
	if checkNoPath tar; then showPathCandidates tar ex; fi

	installerDir=$(getScriptFolderPath)
	cd "$installerDir"
	
	cd jdk
	if [ -e jdk.tar.gz ] 
	then 
		tar xpzf jdk.tar.gz >/dev/null 2>&1
		if [ $? -ne 0 ] 
		then 
			echo "Unable to extract installer executables - aborting installation process"
			exit 3
		fi
		rm jdk.tar.gz
	else 
		if [ -x bin/java ] 
		then 
			echo "Installer is not running for the first time"
		else 
			echo "Installer is corrupt - aborting installation process"
			exit 3
		fi
	fi
	
	cd ..
	
	if [ "$1" = "/NoGUI" ] 
	then 
		IsHeadless="1"
	else 
		IsHeadless="$(./jdk/bin/java -jar "./bin/installer.jar" IsHeadless)"
	fi
	
	if [ "$IsHeadless" = "1" ]; then
		echo "Installing the Sword Connector Server (SCS)"
		echo "Enter the path of the installation folder: "
		read installDest
		if [ ! -d "$installDest" ]; then mkdir -p "$installDest"; fi
		if [ ! -d "$installDest" ]
		then
			echo "Unable to create installation directory ; aborting installation process"
			exit 2
		fi
		./jdk/bin/java -jar ./bin/installer.jar /NoGUI /DestDir "$installDest" /EnvPath "$PATH"
		if [ $? -eq 0 ] 
		then 
			echo "Installation completed successfully - SCS Setup application with start now (press enter to continue)."
			read yesNo
			cd "$installDest"
			./jdk/bin/java -jar scs/bin/setup.jar /EnvPath "$PATH"
		fi
		
	else
		./jdk/bin/java -jar ./bin/installer.jar /EnvPath "$PATH"
	fi
fi


