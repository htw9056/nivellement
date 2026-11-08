// Windows-Starter für Nivellement.
//
// Startet app\Nivellement.jar mit der mitgelieferten Java-Laufzeit (runtime\bin).
// Fehlt sie, wird ein installiertes Java 17+ über JAVA_HOME bzw. PATH gesucht.
// Mit -tags konsole entsteht der Konsolenstarter (java.exe, wartet auf das Ende),
// sonst der Oberflächenstarter (javaw.exe, ohne Konsolenfenster).
package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

func meldung(text string) {
	if konsole {
		os.Stderr.WriteString(text + "\n")
		return
	}
	user32 := syscall.NewLazyDLL("user32.dll")
	box := user32.NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString(text)
	k, _ := syscall.UTF16PtrFromString("Nivellement")
	box.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(k)), 0x10)
}

func vorhanden(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

func javaSuchen(ordner, name string) string {
	eigen := filepath.Join(ordner, "runtime", "bin", name)
	if vorhanden(eigen) {
		return eigen
	}
	if jh := os.Getenv("JAVA_HOME"); jh != "" {
		p := filepath.Join(jh, "bin", name)
		if vorhanden(p) {
			return p
		}
	}
	if p, err := exec.LookPath(name); err == nil {
		return p
	}
	return ""
}

func main() {
	exe, err := os.Executable()
	if err != nil {
		meldung("Programmordner nicht gefunden: " + err.Error())
		os.Exit(1)
	}
	ordner := filepath.Dir(exe)
	jar := filepath.Join(ordner, "app", "Nivellement.jar")
	if !vorhanden(jar) {
		meldung("Programmdatei fehlt:\n" + jar)
		os.Exit(1)
	}
	name := "javaw.exe"
	if konsole {
		name = "java.exe"
	}
	java := javaSuchen(ordner, name)
	if java == "" {
		meldung("Keine Java-Laufzeit gefunden.\n\nDer Ordner 'runtime' fehlt und Java 17 oder neuer ist nicht installiert.")
		os.Exit(1)
	}
	args := append([]string{"-Dsun.java2d.uiScale.enabled=true", "-jar", jar}, os.Args[1:]...)
	cmd := exec.Command(java, args...)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	if konsole {
		if err := cmd.Run(); err != nil {
			if e, ok := err.(*exec.ExitError); ok {
				os.Exit(e.ExitCode())
			}
			meldung("Start fehlgeschlagen: " + err.Error())
			os.Exit(1)
		}
		return
	}
	if err := cmd.Start(); err != nil {
		meldung("Start fehlgeschlagen:\n" + err.Error())
		os.Exit(1)
	}
}
