using System.Diagnostics;

namespace scs.installer
{
	class Program
	{
		static void Main(string[] args)
		{
			string installerRootDir = new System.IO.FileInfo(System.Reflection.Assembly.GetExecutingAssembly().Location).Directory.FullName;
			ProcessStartInfo installInfo = new ProcessStartInfo();
			installInfo.FileName = installerRootDir + "\\jdk\\bin\\javaw.exe";
			string arguments = "-jar \"bin\\installer.jar\" /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\"";
			installInfo.Arguments = arguments;
			Process.Start(installInfo);
		}
	}
}
