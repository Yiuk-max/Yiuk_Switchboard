package main

import (
	"bufio"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"runtime"

	"yiuk-switchboard/internal/server"
)

func main() {
	configPath := flag.String("config", "config.yaml", "path to YAML configuration file")
	flag.Parse()
	resolved, err := resolveConfigPath(*configPath, flagWasSet("config"))
	if err == nil {
		err = server.RunFromConfig(resolved)
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "Relay failed to start: %v\n", err)
		if runtime.GOOS == "windows" {
			if info, statErr := os.Stdin.Stat(); statErr == nil && info.Mode()&os.ModeCharDevice != 0 {
				fmt.Fprint(os.Stderr, "Press Enter to close this window...")
				_, _ = bufio.NewReader(os.Stdin).ReadString('\n')
			}
		}
		os.Exit(1)
	}
}

func flagWasSet(name string) bool {
	set := false
	flag.Visit(func(f *flag.Flag) {
		if f.Name == name {
			set = true
		}
	})
	return set
}

func resolveConfigPath(path string, explicit bool) (string, error) {
	if explicit {
		return path, nil
	}
	candidates := []string{path}
	if executable, err := os.Executable(); err == nil {
		dir := filepath.Dir(executable)
		candidates = append(candidates, filepath.Join(dir, path), filepath.Join(filepath.Dir(dir), path))
	}
	for _, candidate := range candidates {
		if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
			return candidate, nil
		}
	}
	return "", fmt.Errorf("config file %q not found (looked in the working directory and next to the executable); copy configs/config.yaml to config.yaml and set its password", path)
}
