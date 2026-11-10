# Electricity Consumption and Savings Calculator

A Java web app that calculates a household's monthly electricity units and bill from its appliances, shows where the units go, suggests ways to save with the rupee amount for each, and compares the bill before and after a change.

## Features
- Add appliances (name, watts, hours per day, quantity)
- Monthly units and bill using editable slab rates and a fixed charge
- Usage breakdown and a slab-by-slab bill table
- Savings suggestions ranked by rupees saved
- What-if comparison that never changes your real list
- Download a text report

## How to run
You need JDK 17 or later. Run all commands from the project folder (the one that contains `src` and `web`).

**In Eclipse:** File → New → Java Project, untick "Use default location", browse to this folder, untick "Create module-info.java", then right-click `src/electricity/WebApp.java` → Run As → Java Application.

**From a terminal:**

```
javac -d out src/electricity/*.java
java -cp out electricity.WebApp
```

The app opens in your browser at http://localhost:8080/. Stop it with the Stop button in Eclipse, or Ctrl+C in the terminal.

There is also a console version: run `electricity.Main`.

## Project structure
| Part | What it does |
| --- | --- |
| `Appliance` | One appliance and its monthly units |
| `Tariff` | Slab rates and the bill calculation |
| `BillCalculator` | The appliance list, total units and bill |
| `SavingsAnalyzer`, `Suggestion` | Ranking, savings tips, what-if |
| `WhatIfResult` | Result of a what-if comparison |
| `ReportWriter` | Builds the text report |
| `WebApp` | Small web server that connects the classes to the page |
| `web/` | The page: `index.html`, `style.css`, `app.js` |

The Java classes do all the calculations. The page only displays results and sends form values to `WebApp`.

## Working on this project
- Create a branch for each change (`git checkout -b your-change`) and open a pull request.
- Run `git pull` before you start and before you push.
- The slab rates in `Tariff()` are sample values. Use your state's real tariff for a real bill.

