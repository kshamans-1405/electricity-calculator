"use strict";

/* The page only shows things and sends form values to the Java server (WebApp.java).
   All the calculations happen in the Java classes. */

// Typical wattages for the demo. Check the label on your own appliance for the exact value.
const PRESETS = [
  { name: "Air conditioner", watts: 1500, hours: 4 },
  { name: "Ceiling fan", watts: 75, hours: 8 },
  { name: "LED bulb", watts: 9, hours: 6 },
  { name: "Tube light", watts: 40, hours: 6 },
  { name: "Refrigerator", watts: 150, hours: 8, tip: "A fridge compressor runs only part of the day, so about 8 hours is a good estimate." },
  { name: "Television", watts: 100, hours: 4 },
  { name: "Washing machine", watts: 500, hours: 1 },
  { name: "Water heater (geyser)", watts: 2000, hours: 1 },
  { name: "Laptop", watts: 60, hours: 4 },
];

// The household from the project plan: 324.3 units
const SAMPLE_HOUSEHOLD = [
  { name: "Air conditioner", watts: 1500, hours: 4, quantity: 1 },
  { name: "Ceiling fan", watts: 75, hours: 10, quantity: 3 },
  { name: "Tube light", watts: 40, hours: 6, quantity: 4 },
  { name: "Refrigerator", watts: 150, hours: 8, quantity: 1 },
  { name: "Television", watts: 100, hours: 4, quantity: 1 },
];

const $ = (id) => document.getElementById(id);
let state = null;          // everything the server knows: appliances, bill, tariff, suggestions
let whatIfResult = null;   // the latest what-if comparison
let draftSlabs = [];       // the tariff editor: [{ limit: "100", rate: "3" }, ...]

/* ---------- talking to the server ---------- */

async function api(path, params) {
  const options = params
    ? { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(params) }
    : {};
  let response;
  try {
    response = await fetch(path, options);
  } catch (e) {
    throw new Error("Cannot reach the program. Check that it is still running in Eclipse.");
  }
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Something went wrong");
  return data;
}

async function run(task) {
  try {
    await task();
  } catch (e) {
    toast(e.message, true);
  }
}

function setState(newState) {
  state = newState;
  whatIfResult = null;   // the server drops its what-if too whenever the data changes
  loadDraftFromTariff();
  render();
}

/* ---------- small helpers ---------- */

function h(tag, attrs, ...kids) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === false || value === null || value === undefined) continue;
    if (key === "class") node.className = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value);
  }
  for (const kid of kids) {
    if (kid === null || kid === undefined || kid === false) continue;
    node.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return node;   // text is added as text nodes, so names typed by the user can never inject HTML
}

const inr = (n) => "₹" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const units = (n) => Number(n).toFixed(1);
const num = (n) => (Number.isInteger(n) ? String(n) : String(Number(n.toFixed(2))));
const label = (a) => (a.quantity > 1 ? `${a.name} (×${a.quantity})` : a.name);

let toastTimer;
function toast(message, isError = false) {
  const box = $("toast");
  box.textContent = message;
  box.className = "show" + (isError ? " error" : "");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (box.className = ""), 3800);
}

function emptyNote() {
  return h("div", {},
    h("p", { class: "empty" }, "No appliances yet."),
    h("button", { class: "btn btn-primary", type: "button", onclick: () => showView("appliances") }, "Add an appliance"));
}

/* ---------- the odometer ---------- */

const DIGITS = 6;   // five whole digits and the tenths wheel

function buildOdometer() {
  const box = $("odometer");
  for (let i = 0; i < DIGITS; i++) {
    const strip = h("span", { class: "strip" });
    for (let d = 0; d <= 9; d++) strip.append(h("span", {}, String(d)));
    box.append(h("span", { class: "digit" + (i === DIGITS - 1 ? " tenths" : "") }, strip));
    if (i === DIGITS - 2) box.append(h("span", { class: "point" }, "."));
  }
}

function setOdometer(value) {
  const tenths = Math.round(Math.min(Math.max(value, 0), 99999.9) * 10);
  const text = String(tenths).padStart(DIGITS, "0");
  document.querySelectorAll("#odometer .strip").forEach((strip, i) => {
    strip.style.transform = `translateY(-${Number(text[i]) * 10}%)`;
  });
  $("odometer").setAttribute("aria-label", `${units(value)} kilowatt-hours`);
}

/* ---------- drawing the page ---------- */

function render() {
  setOdometer(state.totalUnits);
  $("bill").textContent = inr(state.totalBill);
  const empty = state.appliances.length === 0;
  $("downloadBtn").classList.toggle("disabled", empty);
  $("downloadBtn").setAttribute("aria-disabled", String(empty));

  renderAppliances();
  renderBreakdown();
  renderSavings();
  renderWhatIf();
  renderSlabRows();
}

function renderAppliances() {
  const box = $("applianceList");
  box.replaceChildren();
  if (state.appliances.length === 0) {
    box.append(
      h("p", { class: "empty" }, "No appliances yet. Add one above, or load a sample household to see how the calculator works."),
      h("button", { class: "btn btn-primary", type: "button", onclick: () => run(loadSample) }, "Load sample household"));
    return;
  }
  const rows = state.appliances.map((a) =>
    h("tr", {},
      h("td", {}, a.name),
      h("td", { class: "num" }, num(a.watts)),
      h("td", { class: "num" }, num(a.hours)),
      h("td", { class: "num" }, a.quantity),
      h("td", { class: "num" }, units(a.units)),
      h("td", { class: "act" },
        h("button", { class: "link-danger", type: "button", "aria-label": "Remove " + a.name, onclick: () => run(() => removeAppliance(a.index)) }, "Remove"))));
  box.append(
    h("div", { class: "table-wrap" },
      h("table", { class: "ledger" },
        h("thead", {}, h("tr", {},
          h("th", {}, "Appliance"), h("th", { class: "num" }, "Watts"), h("th", { class: "num" }, "Hours/day"),
          h("th", { class: "num" }, "Qty"), h("th", { class: "num" }, "Units/month"), h("th", {}, ""))),
        h("tbody", {}, ...rows))),
    h("button", { class: "btn btn-quiet", type: "button", onclick: () => run(clearAll) }, "Remove all"));
}

function renderBreakdown() {
  const box = $("breakdownBody");
  box.replaceChildren();
  if (state.appliances.length === 0) {
    box.append(h("h2", {}, "Where the units go"), emptyNote());
    return;
  }
  const sorted = [...state.appliances].sort((a, b) => b.units - a.units);
  const bars = h("div", { class: "bars" });
  for (const a of sorted) {
    bars.append(h("div", { class: "bar-row" },
      h("div", {}, label(a)),
      h("div", { class: "bar-track" }, h("div", { class: "bar-fill", style: `width:${a.share}%` })),
      h("div", { class: "bar-figure" }, `${units(a.units)} units, ${a.share.toFixed(1)}%`)));
  }
  box.append(h("h2", {}, "Where the units go"), bars, h("h2", {}, "How the bill adds up"), slabTable());
}

function slabName(slab) {
  return slab.to === null ? `Above ${num(slab.from)} units` : `${num(slab.from)} to ${num(slab.to)} units`;
}

// Shows how many of your units fall in each slab, the same way Tariff.calculateBill() works
function slabTable() {
  let remaining = state.totalUnits;
  const rows = [];
  for (const slab of state.tariff.slabs) {
    const size = slab.to === null ? Infinity : slab.to - slab.from;
    const used = Math.max(0, Math.min(remaining, size));
    remaining -= used;
    if (used > 0) {
      rows.push(h("tr", {}, h("td", {}, slabName(slab)), h("td", { class: "num" }, units(used)),
        h("td", { class: "num" }, inr(slab.rate)), h("td", { class: "num" }, inr(used * slab.rate))));
    }
  }
  if (state.tariff.fixedCharge > 0) {
    rows.push(h("tr", {}, h("td", { colspan: "3" }, "Fixed charge"), h("td", { class: "num" }, inr(state.tariff.fixedCharge))));
  }
  return h("div", { class: "table-wrap" },
    h("table", { class: "ledger" },
      h("thead", {}, h("tr", {}, h("th", {}, "Slab"), h("th", { class: "num" }, "Your units"),
        h("th", { class: "num" }, "Rate per unit"), h("th", { class: "num" }, "Amount"))),
      h("tbody", {}, ...rows),
      h("tfoot", {}, h("tr", {}, h("td", { colspan: "3" }, "Estimated bill"), h("td", { class: "num" }, inr(state.totalBill))))));
}

function renderSavings() {
  const box = $("savingsBody");
  box.replaceChildren();
  if (state.appliances.length === 0) {
    box.append(emptyNote());
    return;
  }
  if (state.suggestions.length === 0) {
    box.append(h("p", { class: "empty" }, "No suggestions right now. Your usage already looks efficient."));
    return;
  }
  box.append(h("p", { class: "hint" }, "Ranked by monthly saving. Each saving is measured on its own, so don't add them up."));
  for (const s of state.suggestions) {
    box.append(h("div", { class: "save-row" },
      h("div", { class: "save-text" },
        h("div", {}, s.text),
        h("div", { class: "save-units" }, `Saves ${units(s.units)} units a month`)),
      h("div", { class: "save-money" },
        h("div", { class: "save-month" }, inr(s.perMonth) + " a month"),
        h("div", { class: "save-year" }, inr(s.perYear) + " a year"))));
  }
}

/* ---------- what-if ---------- */

function renderWhatIf() {
  const has = state.appliances.length > 0;
  $("whatifForm").hidden = !has;
  $("whatifEmpty").replaceChildren(...(has ? [] : [emptyNote()]));
  if (!has) {
    $("whatifResult").replaceChildren();
    return;
  }
  const select = $("whatifAppliance");
  const previous = select.value;
  select.replaceChildren(...state.appliances.map((a) => h("option", { value: String(a.index) }, label(a))));
  if (previous !== "" && state.appliances[Number(previous)]) select.value = previous;
  fillWhatIfInputs();
  renderWhatIfResult();
}

function fillWhatIfInputs() {
  const a = state.appliances[Number($("whatifAppliance").value)];
  if (a) {
    $("whatifHours").value = a.hours;
    $("whatifQty").value = a.quantity;
  }
}

function renderWhatIfResult() {
  const box = $("whatifResult");
  box.replaceChildren();
  if (!whatIfResult) return;
  const r = whatIfResult;
  const saves = r.savingMonth >= 0;
  const cls = saves ? "saving" : "extra";
  box.append(
    h("h3", {}, `${r.name} for ${num(r.hours)} hours a day, quantity ${r.quantity}`),
    h("div", { class: "table-wrap" },
      h("table", { class: "ledger" },
        h("thead", {}, h("tr", {}, h("th", {}, ""), h("th", { class: "num" }, "Now"), h("th", { class: "num" }, "After the change"))),
        h("tbody", {},
          h("tr", {}, h("td", {}, "Units per month"), h("td", { class: "num" }, units(r.oldUnits)), h("td", { class: "num" }, units(r.newUnits))),
          h("tr", {}, h("td", {}, "Bill"), h("td", { class: "num" }, inr(r.oldBill)), h("td", { class: "num" }, inr(r.newBill)))),
        h("tfoot", {},
          h("tr", {}, h("td", {}, saves ? "You save per month" : "You pay extra per month"), h("td", {}, ""), h("td", { class: "num " + cls }, inr(Math.abs(r.savingMonth)))),
          h("tr", {}, h("td", {}, saves ? "You save per year" : "You pay extra per year"), h("td", {}, ""), h("td", { class: "num " + cls }, inr(Math.abs(r.savingYear))))))));
}

/* ---------- tariff editor ---------- */

function loadDraftFromTariff() {
  const slabs = state.tariff.slabs;
  draftSlabs = slabs.map((s) => ({ limit: s.to === null ? "" : String(s.to), rate: String(s.rate) }));
  $("fixedCharge").value = state.tariff.fixedCharge;
}

function renderSlabRows() {
  const box = $("slabRows");
  box.replaceChildren();
  draftSlabs.forEach((slab, i) => {
    const last = i === draftSlabs.length - 1;
    const limitInput = h("input", { type: "number", min: "0.1", step: "any", value: slab.limit,
      oninput: (e) => (draftSlabs[i].limit = e.target.value) });
    const rateInput = h("input", { type: "number", min: "0", step: "any", value: slab.rate,
      oninput: (e) => (draftSlabs[i].rate = e.target.value) });
    box.append(h("div", { class: "slab-row" },
      h("div", { class: "slab-name" }, `Slab ${i + 1}`),
      last ? h("label", {}, "Units", h("input", { type: "text", value: "No upper limit", disabled: "" }))
           : h("label", {}, "Up to (units)", limitInput),
      h("label", {}, "Rate (₹ per unit)", rateInput),
      !last && draftSlabs.length > 1
        ? h("button", { class: "link-danger", type: "button", "aria-label": `Remove slab ${i + 1}`, onclick: () => removeSlab(i) }, "Remove")
        : null));
  });
  $("addSlab").disabled = draftSlabs.length >= 8;
}

function removeSlab(i) {
  draftSlabs.splice(i, 1);
  renderSlabRows();
}

/* ---------- actions ---------- */

async function removeAppliance(index) {
  setState(await api("/api/remove", { index }));
}

async function clearAll() {
  if (!window.confirm("Remove all appliances?")) return;
  setState(await api("/api/clear", {}));
}

async function loadSample() {
  let latest = null;
  for (const a of SAMPLE_HOUSEHOLD) latest = await api("/api/add", a);
  setState(latest);
  toast("Loaded a sample household");
}

function showView(name) {
  document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== "view-" + name));
  document.querySelectorAll("#tabs button").forEach((b) => {
    const on = b.dataset.view === name;
    b.classList.toggle("active", on);
    b.setAttribute("aria-current", on ? "page" : "false");
  });
}

/* ---------- wiring ---------- */

function setUp() {
  buildOdometer();

  document.querySelectorAll("#tabs button").forEach((b) => b.addEventListener("click", () => showView(b.dataset.view)));

  // common appliances
  const preset = $("preset");
  preset.append(h("option", { value: "" }, "Pick one, or type your own below"));
  PRESETS.forEach((p, i) => preset.append(h("option", { value: String(i) }, `${p.name} (${p.watts} W)`)));
  preset.addEventListener("change", () => {
    const p = PRESETS[Number(preset.value)];
    $("presetTip").textContent = p && p.tip ? p.tip : "";
    if (!p) return;
    $("name").value = p.name;
    $("watts").value = p.watts;
    $("hours").value = p.hours;
    $("hours").focus();
  });

  $("addForm").addEventListener("submit", (e) => {
    e.preventDefault();
    run(async () => {
      const name = $("name").value.trim();
      setState(await api("/api/add", { name, watts: $("watts").value, hours: $("hours").value, quantity: $("quantity").value }));
      toast("Added " + name);
      $("addForm").reset();
      $("presetTip").textContent = "";
      $("preset").focus();
    });
  });

  $("whatifAppliance").addEventListener("change", fillWhatIfInputs);
  $("whatifForm").addEventListener("submit", (e) => {
    e.preventDefault();
    run(async () => {
      whatIfResult = await api("/api/whatif", {
        index: $("whatifAppliance").value, hours: $("whatifHours").value, quantity: $("whatifQty").value });
      renderWhatIfResult();
    });
  });

  $("addSlab").addEventListener("click", () => {
    if (draftSlabs.length < 8) {
      draftSlabs.splice(draftSlabs.length - 1, 0, { limit: "", rate: "" });   // new slab goes before the last one
      renderSlabRows();
    }
  });
  $("tariffForm").addEventListener("submit", (e) => {
    e.preventDefault();
    run(async () => {
      setState(await api("/api/tariff", {
        limits: draftSlabs.slice(0, -1).map((s) => s.limit).join(","),
        rates: draftSlabs.map((s) => s.rate).join(","),
        fixed: $("fixedCharge").value }));
      toast("Tariff saved");
    });
  });
  $("resetTariff").addEventListener("click", () => run(async () => {
    setState(await api("/api/tariff/reset", {}));
    toast("Using the sample slabs");
  }));

  $("downloadBtn").addEventListener("click", (e) => {
    if (state.appliances.length === 0) {
      e.preventDefault();
      toast("Add an appliance before downloading the report.", true);
    }
  });

  run(async () => setState(await api("/api/state")));
}

setUp();