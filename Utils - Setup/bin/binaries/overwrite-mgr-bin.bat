ping localhost

xcopy /C /R /Y scs\bin\newbin\* scs\bin
rmdir /S /Q scs\bin\newbin

xcopy /C /R /Y scs\bin\newroot\* .
rmdir /S /Q scs\bin\newroot

