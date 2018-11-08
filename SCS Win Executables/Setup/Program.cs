using System;
using System.Diagnostics;
using System.IO;
using System.Threading;

namespace scs.setup
{
	class Program
	{
		static void Main(string[] args)
		{
			string scsRootDir = new System.IO.FileInfo(System.Reflection.Assembly.GetExecutingAssembly().Location).Directory.FullName;

            string setupJar;
            if (new FileInfo(scsRootDir + "\\scs\\bin\\setup.jar").Exists)
            {
                setupJar = scsRootDir + "\\scs\\bin\\setup.jar";
            }
            else if (new FileInfo(scsRootDir + "\\gsp\\bin\\setup.jar").Exists)
            {
                setupJar = scsRootDir + "\\gsp\\bin\\setup.jar";
	        }
            else
            {
                setupJar = scsRootDir + "\\gsp\\bin\\GSP Manager.jar";
            }

            string arguments = "-jar \"" + setupJar + "\" /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\"";
            bool inheritsIO = false;

	        if (args.Length > 0 && "/NoGUI".Equals(args[0]))
	        {
                if (args.Length > 1)
                {
                    string a1 = args[1];
                    if ("SvcStart".Equals(a1) || "svcstart".Equals(a1) || "start".Equals(a1) || "/SvcStart".Equals(a1) || "/svcstart".Equals(a1) || "/start".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcStart";
                        inheritsIO = true;
                    }
                    else if ("SvcStop".Equals(a1) || "svcstop".Equals(a1) || "stop".Equals(a1) || "/SvcStop".Equals(a1) || "/svcstop".Equals(a1) || "/stop".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcStop";
                        inheritsIO = true;
                    }
                    else if ("SvcRestart".Equals(a1) || "svcrestart".Equals(a1) || "restart".Equals(a1) || "/SvcRestart".Equals(a1) || "/svcrestart".Equals(a1) || "/restart".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcRestart";
                        inheritsIO = true;
                    }
                    else if ("ApplyPatch".Equals(a1) || "Patch".Equals(a1) || "patch".Equals(a1) || "/ApplyPatch".Equals(a1) || "/Patch".Equals(a1) || "/patch".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode ApplyPatch \"" + args[2] + "\"";
                        inheritsIO = true;
                    }
                }
            }
            else
            {
                if (args.Length > 0)
                {
                    string a1 = args[0];
                    if ("SvcStart".Equals(a1) || "svcstart".Equals(a1) || "start".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcStart";
                        inheritsIO = true;
                    }
                    else if ("SvcStop".Equals(a1) || "svcstop".Equals(a1) || "stop".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcStop";
                        inheritsIO = true;
                    }
                    else if ("SvcRestart".Equals(a1) || "svcrestart".Equals(a1) || "restart".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode SvcRestart";
                        inheritsIO = true;
                    }
                    else if ("ApplyPatch".Equals(a1) || "Patch".Equals(a1) || "patch".Equals(a1))
                    {
                        arguments = "-jar \"" + setupJar + "\" /NoGUI /EnvPath \"" + System.Environment.GetEnvironmentVariable("PATH") + "\" /Mode ApplyPatch \"" + args[1] + "\"";
                        inheritsIO = true;
                    }
                }
            }

            ProcessStartInfo installInfo = new ProcessStartInfo();
            if (inheritsIO) installInfo.FileName = scsRootDir + "\\jdk\\bin\\java.exe";
            else installInfo.FileName = scsRootDir + "\\jdk\\bin\\javaw.exe";

			installInfo.Arguments = arguments;
            Process p = Process.Start(installInfo);
            if (inheritsIO) p.WaitForExit();
		}
	}
}
