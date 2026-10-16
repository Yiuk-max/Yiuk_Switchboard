package main

import (
	"flag"
	"log/slog"
	"os"

	"yiuk-switchboard/internal/server"
)

func main() {
	configPath := flag.String("config", "config.yaml", "path to YAML configuration file")
	flag.Parse()
	if err := server.RunFromConfig(*configPath); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}
