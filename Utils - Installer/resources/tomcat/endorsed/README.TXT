To add endorsed libraries to an application path, add the following parameter to Java bootstrap parameters:
	-Djava.endorsed.dirs="<path-to-endorsed-libs-direcctory>"
	
note: Tomcat server already contains this parameter among its startup parameters. Its value is %CATALINA_HOME%/endorsed.