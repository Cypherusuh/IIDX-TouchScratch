package main

import (
	"fmt"
	"log"
	"time"

	"iidxtouchscratch/internal/spice2x"
)

func main() {
	client, err := spice2x.Connect("127.0.0.1:1337")
	if err != nil {
		log.Fatal(err)
	}
	defer client.Close()

	fmt.Println("Connected to Spice2X")

	analogs, err := client.ReadAnalogs()
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println("\nAvailable analogs:")

	for _, analog := range analogs {
		fmt.Printf(
			"%-20s value=%.2f active=%v\n",
			analog.Name,
			analog.State,
			analog.Active,
		)
	}

	fmt.Println("\nOverriding Turntable P1 to 0.6...")

	if err := client.WriteAnalog("Turntable P1", 0.6); err != nil {
		log.Fatal(err)
	}

	fmt.Println("Override successful.")
	fmt.Println("Check Spice2X Config. Resetting in 3 seconds...")

	time.Sleep(3 * time.Second)

	if err := client.ResetAnalog("Turntable P1"); err != nil {
		log.Fatal(err)
	}

	fmt.Println("P1 restored successfully.")
}
