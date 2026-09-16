# Piano di riorganizzazione UX/UI — ExpensesTracker

Documento di lavoro per una sessione Claude (Opus o Sonnet) che deve implementarlo.
È autosufficiente: parte dallo stato attuale dell'app, riassume cosa fanno le app di
riferimento sul mercato, e definisce un piano a fasi con criteri di accettazione.

Data: 2026-09-13. Ultimo commit di riferimento: `a09aa2c`.

---

## 0. Contesto tecnico (leggere prima di toccare codice)

- Android, Kotlin, Jetpack Compose + Material 3, Firebase Auth (anonimo + link Google) e
  Firestore. Compose BOM `2024.09.00`, AGP 8.9.1, Kotlin 2.3.0, minSdk 26.
- Package `com.example.expensestracker`. Struttura: `data/` (model, repository, settings),
  `domain/` (BalanceCalculator, RecurringSchedule/Generator), `ui/` (una cartella per
  schermata + `components/` + `theme/`), `util/` (Formatting, CategoryDisplay).
- Due utenti al massimo per gruppo. Le categorie sono **private per utente**
  (`users/{uid}/categories`): una spesa condivisa creata dal partner porta un `categoryId`
  che l'altro non ha. `domain/CategoryResolver` la attribuisce alla categoria del
  visualizzatore **per nome** (i nomi di default corrispondono in ogni lingua, quelli
  personalizzati per uguaglianza case-insensitive), altrimenti ad "Altro". Il bucket
  sintetico "Condivise" (`MonthViewModel.SHARED_BUCKET_ID`) resta solo come ultima
  rete se l'utente ha cancellato "Altro". Usare sempre `uiState.categoryExpenses`
  (spese del mese già raggruppate per categoria risolta), mai filtrare per
  `expense.categoryId` in UI.
- Valuta base: EUR (`DefaultUserData.BASE_CURRENCY`); ogni spesa salva `amount` +
  `currencyCode` + `amountInBaseCurrency`. Le ricorrenti NON salvano l'importo in base:
  si converte in UI con `currencyRates`.
- Preferenze locali (DataStore, `SettingsRepository`): budget mensile, budget per
  categoria, tema, gruppo, valuta predefinita, default "condivisa" per spese/ricorrenti.
- Stringhe: `values/strings.xml` (EN) e `values-it/strings.xml` (IT). Ogni nuova stringa va
  in entrambi.
- Build da CLI: serve JDK ≤ 23. Temurin 21 è in
  `%LOCALAPPDATA%\Android\jdk\jdk-21.0.12.1+1` (se manca, riscaricare da Adoptium e
  scompattare lì). SDK in `%LOCALAPPDATA%\Android\Sdk`,
  `local.properties` già presente e gitignored. `app/google-services.json` è presente e
  gitignored. Il keystore debug è `%USERPROFILE%\.android\debug.keystore`: NON rigenerarlo,
  altrimenti l'APK non si installa sopra quello già sul telefono dell'utente.
  Comando: `JAVA_HOME=<jdk> ./gradlew.bat assembleDebug`.

## 1. Stato attuale (cosa c'è oggi)

Navigazione: 4 tab in basso — **Home**, **Ricorrenti**, **Categorie**, **Impostazioni** —
più un FAB "+" (Home) / FAB esteso (Categorie, Ricorrenti) ospitato nello Scaffold di
`MainActivity`.

- **Home** (`ui/dashboard`): pager orizzontale per mese; in ordine verticale: card
  "Speso questo mese" con budget e barra allocazione; sezione "Per categoria" (righe
  con barra budget, tap → sheet con le spese di quella categoria); card Saldo (solo in
  gruppo, totale a vita); lista piatta "Spese recenti" del mese (tap → sheet dettaglio con
  Modifica/Elimina).
- **Inserimento spesa** (`ui/addexpense`): bottom sheet lungo, nell'ordine: importo,
  chip valuta, picker categorie (riga scorrevole di cerchi), switch "condivisa" + pagato
  da/split, data, nota, Salva. Ha guardia anti-perdita dati e messaggi d'errore.
- **Ricorrenti**: card totale mensile, lista con nota/categoria/frequenza/prossima
  scadenza/importo/switch attivo; tap → sheet dettaglio. Dialog di inserimento (AlertDialog
  scrollabile, molto lungo).
- **Categorie**: card budget totale con barra allocazione; lista con frecce riordino,
  icona, nome, budget, menu ⋮; dialog di modifica (icona/colore preset, budget).
- **Impostazioni**: Account (link Google), Gruppo, Preferenze (valuta default, default
  condivisa), Tema, Valute (tassi).
- **Tema** (`ui/theme`): palette "Scandi" (blu fiordo, corallo, verde mare, sabbia),
  light/dark, dynamic color disattivato. Nessuna libreria grafici; nessuna icona custom.

Problemi rilevati dall'uso reale (conversazioni con l'utente):
1. Home troppo affollata e verticale: budget, categorie, saldo e lista si contendono
   lo spazio; il saldo (a vita) accanto ai numeri del mese confonde.
2. Ricorrenti e Categorie sono schermate di *gestione* ma occupano due tab su quattro.
3. L'inserimento spesa è un modulo lungo: per il caso frequente (importo + categoria)
   si scorre troppo.
4. Manca una vista "storico" vera (ricerca, filtri, raggruppamento per giorno) e manca
   qualunque vista di tendenza (mese su mese).
5. La parte "coppia" è un'unica card: non si vede l'attività condivisa, chi ha pagato cosa,
   né un flusso chiaro di "salda".
6. Componenti duplicati: `ExpenseRow`/`DetailRow`/sheet di dettaglio esistono in copie
   quasi identiche in `DashboardScreen.kt` e `RecurringScreen.kt`.

## 2. Cosa fanno le app di riferimento

Sintesi delle convenzioni ricorrenti (Monarch, Copilot, Spendee, YNAB per il personale;
Monzo/Revolut per le liste; Splitwise, Tricount, Settle Up per la coppia). Fonti in fondo.

| Area | Pattern di mercato | Oggi nell'app |
|---|---|---|
| Home | Un numero "hero" + un grafico + attività recente. Tutto il resto dietro un tap (progressive disclosure). Dashboard modulari. | Tutto impilato in una schermata |
| Lista transazioni | Raggruppate per giorno con subtotale, ricerca e filtri (categoria, persona, periodo), icona categoria + nota in evidenza, importo allineato a destra con numerali tabulari | Lista piatta del mese, nessuna ricerca |
| Inserimento | "Amount-first": tastierino numerico grande, categoria a chip, dettagli avanzati collassati; memoria dell'ultima categoria; inserimento in ≤3 tap | Modulo lungo con tutti i campi visibili |
| Categorie/budget | Donut o barre del mese, tap su un segmento → drill-down; budget per categoria come barra con "rimanente" | Righe con barra (ok) ma senza grafico d'insieme |
| Tendenze | Barre mese-su-mese (6–12 mesi), confronto con mese precedente, "media giornaliera" | Assente |
| Coppia / split | Sezione dedicata: hero "chi deve a chi", feed attività (spese + saldi), suggerimento "salda con N pagamenti", totale pagato per persona | Una card in Home |
| Colore | Semantico e coerente: verde = a tuo favore / sotto budget, rosso = a tuo sfavore / sopra budget, ambra = attenzione | Parziale (barra budget sì, saldo usa container M3) |
| Vuoti / onboarding | Empty state con azione, tip contestuali | Card vuota generica |

## 3. Proposta di riorganizzazione

### 3.1 Navigazione (nuova IA) — ✅ Fase 1 completata (2026-09-13)

Quattro tab + FAB centrale, con gestione spostata sotto "Altro":

```
[ Home ]   [ Storico ]   ( + )   [ Statistiche ]   [ Altro ]
```

Stato reale dopo l'implementazione (leggere prima di ripartire dalla Fase 2):

- Il FAB "+" è agganciato ("cradle") sopra il bordo della `NavigationBar`, non messo nello
  slot `floatingActionButton` dello Scaffold: quello slot con `FabPosition.Center` lo
  piazzava 16dp sopra la barra, e visivamente si leggeva come un cerchio staccato che
  galleggiava sopra, non come parte della barra (feedback utente, 2026-09-14). Ora è un
  `FloatingActionButton` dentro lo stesso `Box` della `NavigationBar` in `MainActivity.BottomBar`,
  con `Modifier.offset(y = -28.dp)` (metà del suo diametro standard di 56dp) così è per metà
  sopra il bordo e per metà sovrapposto alla barra. **Non** incassato in una tacca reale
  (nessuna forma scallopata sulla `NavigationBar`, niente `BottomAppBar`): il `Box` non
  ritaglia i figli, quindi il FAB disegnato sopra basta a dare l'effetto senza rischi di
  clipping. Nella `NavigationBar` la quinta colonna centrale è uno `Spacer(Modifier.weight(1f))`
  sempre presente (anche in Altro, dove il FAB non c'è) così le quattro voci non si spostano
  cambiando tab. Le liste chiudono comunque con uno `Spacer(72.dp)` per lasciare aria sotto
  l'ultimo elemento.
- Ricorrenti/Categorie/Impostazioni sono rotte "figlie" di Altro: `Screen.isTab` è falso per
  loro, quindi la TopAppBar mostra la freccia indietro e la bottom bar sparisce. I loro FAB
  estesi restano nello Scaffold root, in `FabPosition.End`.
- `DashboardViewModel` è diventato `ui/month/MonthViewModel` (+ `MonthUiState`), **istanza
  unica creata in `ExpensesTrackerRoot`** e passata a Home/Storico/Statistiche: sono tre
  viste sugli stessi dati, una sola serie di listener Firestore. Le chiavi di `viewModel(key=)`
  sono ora prefissate (`month:`/`addExpense:`) perché una chiave esplicita sostituisce quella
  per-classe e due VM diversi con la stessa chiave collidono.
- `monthLabel` è uscito dallo stato: è `util/formatMonthLabel(YearMonth)` (+ `formatMonthName`
  per "Speso a settembre"), così il pager può etichettare una pagina senza attendere il flow.
- Nuovi condivisi in `ui/month/`: `MonthPager` (header ‹mese› + pagine), `currentMonthState`
  (stato della pagina "ferma", per sheet e dialog che stanno fuori dal pager),
  `MonthDetailState`/`MonthDetailSheets` (i due sheet dettaglio, cablati una volta sola per
  tutte e tre le viste).
- Nuovi condivisi in `ui/components/`: `CategorySpendingRow`, `ExpenseDetailSheet`,
  `CategoryDetailSheet` (+ `CategoryDetailData`), `MonthSummaryCard` (ex `BudgetOverviewCard`,
  ora con la riga "sono X€ al giorno"), `DayHeader`; `SectionHeader` accetta un'azione in coda
  ("Vedi tutte").
- `MonthSummaryCard` e la card Saldo in Home usano `MaterialTheme.semanticColors` (primo uso
  reale della palette semantica introdotta in Fase 0).
- **Statistiche in questa fase è volutamente parziale**: riepilogo del mese + lista completa
  per categoria tappabile (quella che l'utente voleva conservare dalla vecchia Home). I
  grafici (barre 6 mesi, donut, confronto col mese scorso) restano alla Fase 4 — nessun
  placeholder finto in attesa.

- **Home**: sintesi del mese corrente, non del mese selezionato (il selettore mese va in
  Storico e Statistiche). Contenuto, in ordine: hero "Speso a settembre" con barra budget e
  "rimangono X€ · Y€/giorno"; lista "Per categoria" come oggi (righe con barra budget,
  ordinate per speso, tap → sheet con le spese di quella categoria), limitata alle prime
  4-5 con "Vedi tutte" → Statistiche; card Gruppo (solo in gruppo): "Marta ti deve 6,12 €"
  + pulsante Salda + link "Vedi attività"; "Ultime 5 spese" con link "Vedi tutte" →
  Storico.
- **Storico**: pager mese (quello attuale) + ricerca testo + chip filtro (categoria,
  personale/condivisa, chi ha pagato); lista raggruppata per giorno con subtotale
  giornaliero; tap → dettaglio (sheet esistente). Le ricorrenti generate mostrano un
  piccolo badge "🔁".
- **(+)**: quick-add (vedi 3.3). FAB centrale sempre visibile tranne dentro i sheet.
- **Statistiche**: pager mese; barre 6 mesi (speso vs budget); donut compatto come
  riepilogo; sotto, la **stessa lista per categoria tappabile** di Home (tutte le
  categorie, con barra budget e drill-down alle spese raggruppate); "confronto col mese
  scorso" (▲▼ %) e media giornaliera.
- **Altro**: lista di voci — Ricorrenti, Categorie e budget, Gruppo, Account,
  Preferenze, Tema, Valute. (Ricorrenti e Categorie diventano schermate secondarie con
  freccia indietro; il FAB esteso resta nello Scaffold root come oggi.)

Motivazione: Home diventa "a colpo d'occhio", Storico e Statistiche coprono "cerca" e
"capisci", la gestione esce dalla barra. Le etichette corte evitano l'overflow già visto.

### 3.2 Design system (da fare PRIMA delle schermate) — ✅ Fase 0 completata (2026-09-13)

Mantenere la palette Scandi, ma formalizzare. Stato reale dopo l'implementazione (leggere
prima di ripartire dalla Fase 1, perché diverge un po' dalla proposta iniziale):

- `ui/theme/Semantic.kt`: fatto — `positive`/`negative`/`warning`/`neutral` (+ varianti
  `on*`/`*Container`), accessibili via `MaterialTheme.semanticColors`. **Non ancora usato
  da nessuna schermata esistente** (avrebbe cambiato colori già visibili, fuori scope
  Fase 0) — è pronto per la Fase 1 (Home) e la Fase 5 (Gruppo, il saldo nel mockup usa
  proprio verde/rosso semantico).
- `ui/theme/MoneyStyle.kt`: fatto — `Large/Medium/Small`, tutti con `fontFeatureSettings
  = "tnum"`. Usato da `AmountText` e dai due `DetailSheet` (spesa/ricorrente); non ancora
  nelle righe lista (restano sulle Typography esistenti per non cambiare le dimensioni
  del testo già visibili).
- `ui/theme/Spacing.kt`: fatto — `Space.xs/s/m/l/xl`. **Non ancora applicato** alle
  schermate esistenti (sostituire i `.padding()`/`Spacer` sparsi è un cambio cosmetico,
  meglio farlo schermata per schermata quando la si tocca comunque in una fase successiva,
  non come sostituzione meccanica a freddo).
- Raggio card: **non introdotto un nuovo valore**. Verificato che tutte le `Card()` già
  usano lo shape di tema (`AppShapes.medium` = 16 dp) di default - non c'era
  l'incoerenza ipotizzata nella bozza iniziale, quindi niente da formalizzare qui.
- Componenti condivisi in `ui/components/` (fatto):
  - `ExpenseRow.kt` — spostato da `DashboardScreen.kt`, ora importabile da qualunque
    schermata futura (Storico, Gruppo).
  - `AmountText.kt` — EUR primario + valuta originale piccola sotto; adottato da
    `ExpenseRow`, `RecurringRow`, e i due `DetailSheet`.
  - `DetailSheet.kt` — shell condivisa (icona/titolo/sottotitolo, slot importo, divider,
    slot contenuto, footer Elimina/Modifica) + `DetailRow`; sostituisce le due copie
    quasi identiche in Dashboard e Recurring.
  - `SectionHeader.kt`, `EmptyState.kt` — spostati/unificati da Dashboard (Recurring usava
    una empty-card inline leggermente diversa, ora usa lo stesso componente).
  - `BudgetProgressBar.kt` — **diverso dalla proposta**: qui è solo la forma della barra
    (altezza + bordi arrotondati + track), non decide colore/testo. I 5 punti che la usano
    (card budget totale, card allocazione categorie, riga categoria in Home, le due barre
    di Categorie) avevano ciascuno una propria logica di colore legittimamente diversa
    (blu/ambra/rosso a 3 livelli, verde/rosso a 2 livelli, colore della categoria stessa) -
    unificarle avrebbe cambiato colori già visibili. Chi la chiama continua a decidere
    `progress` e `color`.
  - **Non creati**: `CategoryChip` e `DayHeader` — nessuna schermata li usa ancora oggi
    (servono da Fase 2/3 in poi); crearli ora sarebbe stato codice morto. Aggiungerli
    quando la Fase che li introduce li usa per davvero.
- Vico (`com.patrykandpatrick.vico:compose-m3:3.2.2`) aggiunto come dipendenza in
  `build.gradle.kts`, non ancora usato (nessun grafico in questa fase) - pronto per la
  Fase 4. Il donut nei mockup è disegnato a mano con SVG/Canvas, come da piano.
- Icone: nessun cambiamento di dimensione dei cerchi in questa fase (avrebbe alterato
  layout già visibili) - la normalizzazione 40/48/32 dp resta un'attività della fase che
  introduce le nuove schermate (Storico/quick-add), non di questa.

### 3.3 Quick-add (nuovo inserimento spesa) — ✅ Fase 2 completata (2026-09-13)

Sostituisce il sheet attuale mantenendo la stessa `AddExpenseViewModel` e la guardia
anti-perdita dati.

Passo 1 (sempre visibile, nessuno scroll):
- Importo grande in alto (stile `MoneyLarge`), tastierino custom numerico sotto (0-9,
  virgola, backspace) — evita la tastiera di sistema che copre metà schermo.
- Sotto l'importo: chip valuta (solo se >1 valuta) e riga scorrevole categorie con
  l'ultima usata preselezionata (nuova preferenza `lastUsedCategoryId` in DataStore).
- Riga "Oggi · Personale · Nessuna nota" tappabile che apre il Passo 2.
- Pulsante primario "Salva" fisso in basso.

Passo 2 (sezione espandibile o seconda pagina del sheet): data, condivisa + pagato da +
split, nota. Se l'utente è in gruppo e il default "condivisa" è on, la riga riassunto lo
mostra già senza aprire il Passo 2.

Extra: nel dettaglio spesa aggiungere "Duplica" (crea una nuova spesa precompilata).

Stato reale dopo l'implementazione (leggere prima di ripartire dalla Fase 3):

- Il Passo 2 è una **sezione espandibile che prende il posto del tastierino**, non una
  seconda pagina: l'importo e la categoria restano visibili mentre si cambiano data /
  condivisione / nota, e l'altezza del sheet non salta. La riga riassunto fa da toggle.
- Il contenuto **resta dentro un `verticalScroll`**: a conti fatti sta in ~660 dp e su un
  telefono da 6" non scorre, ma la rete di sicurezza serve per schermi corti, landscape e
  font scale grandi. Non è la stessa cosa di "modulo da scorrere": in uso normale non
  scorre.
- Il separatore decimale del tastierino viene dal locale (`util/decimalSeparator()`), non è
  una stringa tradotta: deve coincidere con quello che `String.format` produce nel prefill,
  altrimenti l'importo precompilato non è più modificabile con i tasti.
- `ExpensePrefill(source, isEdit)` sostituisce `editingExpense`: modifica e duplica
  riempiono il form allo stesso modo ma al salvataggio fanno cose opposte, e un booleano
  esplicito è più leggibile di un id vuoto come sentinella. Il duplicato è **datato oggi**,
  non alla data dell'originale.
- La categoria precompilata passa da `CategoryResolver` (prima: primo della lista). Una
  spesa del partner porta un `categoryId` che qui non esiste, ma il nome sì: ora
  modificandola si apre già sulla categoria giusta invece che sulla prima.
- `lastUsedCategoryId` si scrive **solo dopo il salvataggio di una spesa nuova**: modificando
  una spesa vecchia si corregge il passato, non si dichiara cosa si comprerà dopo.
- `AmountKeypad` sta in `ui/components/` con `appendAmountKey` come funzione pura accanto
  (max 7 cifre + 2 decimali, rifiuta il tasto invece di mostrare errori) - è il pezzo da
  riusare se in futuro anche le ricorrenti passano al tastierino.
- **Correzione post-fase (2026-09-14, feedback utente)**: la nota **non** è nel Passo 2
  espandibile come da proposta iniziale - nell'uso reale è compilata quasi sempre ed è più
  una descrizione della spesa che un dettaglio accessorio. Ora è un campo sempre visibile
  subito sotto il selettore categoria (fino a 2 righe, non singleLine, perché una vera
  descrizione a volte è lunga). Il riassunto a scomparsa (`DetailsSummaryRow`) mostra solo
  data e condivisione - le uniche due cose che cambiano davvero "a volte" - e non cita più
  la nota.

### 3.4 Storico — ✅ Fase 3 completata (2026-09-14)

- ✅ Fatto in Fase 1: `ui/history/HistoryScreen` sopra `MonthViewModel.uiStateFor` (niente
  ViewModel dedicato: è la stessa vista mese).
- ✅ Fatto in Fase 1: raggruppamento `groupBy { localDate }` (l'ordine desc arriva già da
  `monthExpenses`), header giorno "Oggi / Ieri / gio 12 set" + subtotale, in
  `ui/components/DayHeader`.
- Ricerca: `TextField` in alto, filtra su nota e nome categoria (già localizzato con
  `localizedCategoryName`). Filtri: chip "Tutte / Personali / Condivise", chip categoria
  (multi), chip "Pagate da me / da Partner" (solo in gruppo).
- Stato vuoto per ricerca senza risultati.

Stato reale dopo l'implementazione (leggere prima di ripartire dalla Fase 4):

- Barra di ricerca e chip filtro sono in `ui/history/HistoryFilters.kt`
  (`HistoryFilterState`, `HistoryFilterBar`, `matchesHistoryFilters`), **fuori dal pager**
  (in `HistoryScreen`, sopra `MonthPager`) così sopravvivono allo swipe tra mesi invece di
  azzerarsi ad ogni pagina. Lo stato filtro usa `remember` semplice, non `rememberSaveable`
  (coerente con `showDetails` in `AddExpenseSheet`): si perde a un kill del processo, non
  è un bug funzionale, e un `Set<String>` non è banale da affidare a un `Bundle`.
- Il filtro categoria confronta contro la categoria **già risolta** da `MonthViewModel`
  (`uiState.categoryExpenses`, che usa `CategoryResolver`), mai `expense.categoryId` grezzo:
  altrimenti una spesa condivisa inserita dal partner - il cui `categoryId` esiste solo
  nella sua lista privata - non comparirebbe mai selezionando quella categoria.
  `DayGroupedExpenses` costruisce una mappa inversa `expenseId -> categoryId` da
  `categoryExpenses` per questo.
- Le chip categoria vengono dalla pagina **assestata** (`currentMonthState`), non da quella
  in scorrimento, così non cambiano mentre si swipa; è la lista completa delle categorie
  dell'utente (anche a spesa zero questo mese) meno il bucket sintetico "Condivise", che
  non è una categoria filtrabile.
- Due stati vuoti distinti: "Nessuna spesa in questo mese" (mese davvero vuoto) vs "Nessuna
  spesa corrisponde ai filtri" (`HistoryFilterState.isActive`), con icona diversa (🧾 vs 🔍).
- Chip "Pagate da me/da Partner" sono a scelta singola con deseleziona-ritoccando (come lo
  scope Tutte/Personali/Condivise), non multi-select: il pagatore di una spesa è sempre uno
  dei due, quindi "entrambi selezionati" non avrebbe senso.

### 3.5 Statistiche — ✅ Fase 4 completata (2026-09-14)

- Barre 6 mesi (Vico): speso per mese in EUR, linea/marker del budget se impostato,
  mese corrente evidenziato, tap su barra → cambia mese del pager.
- Donut categorie del mese selezionato con legenda (nome, importo, %); tap → drill-down
  (riusa il sheet "dettaglio categoria" già esistente).
- Card "Rispetto a agosto": totale ▲/▼ %, e le 3 categorie con la variazione maggiore.
- Card "Ritmo": media giornaliera e proiezione fine mese (spesa/giorni trascorsi × giorni
  del mese) — solo per il mese corrente.

Stato reale dopo l'implementazione (leggere prima di ripartire dalla Fase 5):

- **API Vico usata senza documentazione online** (nessun accesso a internet in questa
  sessione): ricostruita leggendo le classi compilate della dipendenza già presente
  (`compose-m3-android-3.2.2.aar`/`compose-android-3.2.2.aar` nella cache Gradle locale) con
  `javap`, poi verificata per davvero facendo compilare `MonthlyTrendChart.kt` - ha compilato
  al primo tentativo con zero errori, il che conferma che nomi e ordine dei parametri usati
  (`rememberColumnCartesianLayer(columnProvider = ...)`, `HorizontalAxis.rememberBottom(valueFormatter = ...)`,
  `rememberLineComponent(fill = Fill(...), thickness = ..., shape = ...)`,
  `ColumnCartesianLayer.ColumnProvider.series(...)`, `ProvideVicoTheme(rememberM3VicoTheme())`,
  `producer.runTransaction { columnModel { series(valori) } }`) sono corretti - ma **non è
  stato verificato a schermo**: questa macchina non ha un emulatore né un device ADB
  collegato in questa sessione. Prima di fidarsi ciecamente del grafico, aprire la tab
  Statistiche sul telefono e controllare che le barre si vedano, le etichette dei mesi non si
  sovrappongano e il tap su una barra cambi davvero mese.
- **Niente asse valori, niente marker budget, niente evidenziazione colorata del mese
  corrente** sul grafico a barre - tre deviazioni deliberate dalla proposta iniziale, tutte
  per restare dentro l'API Vico che avevo potuto verificare per compilazione:
  - l'asse valori avrebbe richiesto `VerticalAxis.rememberStart(...)` (mai esercitato);
  - la linea/marker budget avrebbe richiesto le `Decoration`/`HorizontalLine` di Vico (mai
    esercitate);
  - evidenziare UNA barra con un colore diverso dalle altre richiede un `ColumnProvider`
    personalizzato per-indice (`series(List<LineComponent>)` mappa a *serie* diverse, non a
    barre diverse nella stessa serie) - il mese visualizzato è comunque riconoscibile perché
    **è sempre l'ultima barra a destra** (la finestra di 6 mesi termina sempre al mese
    visualizzato), quindi la posizione lo rende implicito.
  - Il tap sulle barre **non** usa il sistema marker/interaction di Vico: è un
    `Modifier.pointerInput` con `detectTapGestures` sopra il grafico, che divide la larghezza
    in `months.size` colonne uguali - più semplice e indipendente dall'hit-testing interno
    di Vico, dato che le barre sono già equispaziate.
- Il donut è disegnato a mano con `Canvas`/`drawArc` (`ui/components/CategoryDonutChart.kt`),
  come già previsto dalla nota di Fase 0 - non Vico (che non fa a grafici a torta in questa
  versione senza uno sforzo di configurazione paragonabile a quello del grafico a barre).
- Trend a 6 mesi e confronto col mese precedente (`MonthViewModel.monthlyTrendFor`/
  `comparisonFor`) leggono dallo stesso `allExpenses` già in memoria (nessuna query
  Firestore aggiuntiva: `observeAllExpenses()` non ha mai avuto un limite di data). Sono
  **per-pagina** del pager (ricalcolati ad ogni mese sfogliato, come `uiStateFor`), non
  ancorati al mese "assestato": scorrendo i mesi la finestra di 6 barre e il confronto
  scorrono insieme, coerentemente con "tap su barra → cambia mese" che ha senso solo se la
  finestra segue il mese visualizzato.
- Card "Ritmo" **non compare mai su un mese diverso da quello corrente** (nessuna proiezione
  ha senso per un mese passato o futuro) - condizione `yearMonth == YearMonth.now()`.
- **Correzione post-fase (2026-09-14, feedback utente)**: la media giornaliera e la
  proiezione grezze (`totalSpent / giorni trascorsi × giorni del mese`) sono ingannevoli
  quando il mese contiene spese grosse una tantum (alloggio, trasporti di un viaggio, ecc.):
  proiettarle come se si ripetessero ogni giorno gonfia enormemente la stima. Ora
  `domain/PaceCalculator` esclude dal *ritmo* (non dal totale già speso, quello resta reale)
  le spese "fuori scala": più di 3× la spesa mediana del mese, calcolata sulla quota
  dell'utente (`shareFor(myUid)`), non per categoria (niente elenco di categorie "grosse" da
  mantenere - si adatta a qualunque mese/utente). Sotto le 3 spese nel mese non si tenta
  nemmeno il calcolo (una mediana su 1-2 valori non è significativa). La proiezione resta
  `speso finora (spese fuori scala incluse) + media giornaliera "regolare" × giorni
  rimanenti` - non riparte da zero, il già speso è reale. La card mostra quante spese sono
  state escluse e per quanto ("Esclude 1 spesa grande occasionale (350,00 €)") invece di
  applicare il filtro in silenzio.
- Colori: aumento di spesa rispetto al mese scorso = `semanticColors.negative` (rosso),
  diminuzione = `.positive` (verde) - **l'opposto** della convenzione del saldo di coppia,
  perché qui "di più" è la direzione indesiderata.

### 3.6 Gruppo — ✅ Fase 5 completata (2026-09-14)

Schermata sotto "Altro" (e card riassuntiva in Home). Sostituisce sia la card Saldo di
Home sia la sezione Gruppo di Impostazioni: **nulla viene tolto**, si unisce in un posto.
- Testata: membri ("Tu & Marta"), codice invito con "Copia", "Esci dal gruppo" nel menu ⋮.
  Se non si è in un gruppo, la schermata mostra la `GroupSetupSection` attuale
  (crea / unisciti) al posto di tutto il resto.
- Hero saldo con colore semantico: verde "ti deve", rosso "devi", grigio "in pari"; sotto
  la nota "totale complessivo di sempre" già presente.
- "Come si è formato": pagato da te / pagato da lei in totale (somma `amountInBaseCurrency`
  delle condivise per `paidByUid`), e saldi registrati.
- Feed attività: spese condivise e saldi in ordine cronologico, con `ExpenseRow` e una
  riga dedicata per i saldi ("Marta → Tu 40 €").
- Pulsante "Salda" (dialog esistente) precompilato con l'importo del saldo.
- Una riga di aiuto: "le spese inserite da Marta contano nella tua categoria con lo stesso
  nome (o in Altro); nel tuo budget vale solo la tua quota".

Stato reale dopo l'implementazione (leggere prima di ripartire dalla Fase 6):

- Nuovo `ui/group/GroupViewModel` (non month-scoped: legge `observeAllExpenses()` +
  `observeSettlements()` del gruppo per l'intera vita del gruppo, non filtrato per mese -
  "chi deve a chi" è per definizione una cifra a vita). Registrato in `AppViewModelFactory`.
  `GroupActivityItem` (sealed interface `ExpenseActivity`/`SettlementActivity`) unisce spese
  condivise e saldi in un solo feed ordinato per data poi per `createdAt`.
- `SettlementDialog` **spostato** da `HomeScreen.kt` (copia privata) a
  `ui/components/SettlementDialog.kt`, condiviso da Home e Gruppo, con due parametri nuovi
  (`initialAmount`, `initialIPaid`) per la precompilazione richiesta dal piano: aprendolo da
  un saldo non pari, importo e direzione ("Ho pagato io" / "Mi ha pagato lei") sono già
  impostati in base a chi deve a chi.
- **Sezione Gruppo rimossa da Impostazioni**, non solo spostata: `SettingsViewModel` ha
  perso `group`/`leaveGroup()`/`isLeaving` (restano solo in `GroupViewModel`), ha tenuto
  `inGroup: Boolean` perché `PreferencesCard` lo usa ancora per mostrare/nascondere i
  due toggle "condivisa di default". `GroupSetupSection` (crea/unisciti) resta lo stesso
  componente condiviso, ora montato da `GroupScreen` invece che da `SettingsScreen`.
  `Gruppo` è una voce propria sotto Altro → Gestione (icona persone), non più raggiungibile
  da Impostazioni.
- Home: la card Saldo ha ora due pulsanti invece di uno - "Salda" (come prima) e "Vedi
  attività" (nuovo, apre `GroupScreen`). Era già previsto nel testo originale della Fase 1
  (3.1) ma non era mai stato implementato perché la schermata Gruppo non esisteva ancora.
- Nel feed attività di Gruppo, Elimina e Duplica sulla scheda dettaglio spesa sono
  collegati per davvero (`GroupViewModel.deleteExpense`, `onDuplicateExpense` passato da
  `MainActivity` come altrove) - non finte azioni che chiudono solo il foglio.

### 3.7 Rifiniture trasversali — ✅ Fase 6 completata (2026-09-14)

- Empty state con azione su ogni lista (Home, Storico, Ricorrenti, Categorie, Coppia).
- Feedback: Snackbar "Spesa salvata" con "Annulla" (soft-delete entro 5 s) dopo salva ed
  elimina.
- Animazioni leggere: `animateItemPlacement` nelle liste, `AnimatedContent` sui numeri
  hero, haptic al salva.
- Accessibilità: contentDescription su tutte le icone-azione, contrasto testi su
  container colorati verificato in dark mode.
- Widget home screen "Speso questo mese" (opzionale, ultima cosa).

Stato reale dopo l'implementazione:

- **Empty state con azione**: aggiunta a Storico (due varianti: "Rimuovi filtri" quando i
  filtri nascondono tutto, "Aggiungi spesa" quando il mese è davvero vuoto), Statistiche,
  Ricorrenti, Categorie (nuovo - prima non esisteva alcun empty state: caso limite se si
  cancellano tutte le categorie) e Gruppo. Home ce l'aveva già dalla Fase 1.
- **Snackbar "Spesa salvata"/"Spesa eliminata" con Annulla**: scope deliberatamente limitato
  alle spese (il messaggio di esempio nel piano è "Spesa salvata" - non estesa a
  ricorrenti/categorie/saldi). Elimina è **immediato**, non differito: `Annulla` non
  ripristina un salvataggio in sospeso, **ricrea** la spesa da zero
  (`MonthViewModel.restoreExpense`/`GroupViewModel.restoreExpense`, stessi dati, id nuovo).
  Scelta deliberata rispetto a "nascondi otticamente e cancella solo allo scadere del
  timer": più semplice da implementare corretamente su più schermate (Home/Storico/
  Statistiche/Gruppo) e Firestore resta sempre coerente con quello che l'utente vede,
  a costo di un breve flash alla cancellazione se poi si annulla. Una barra di snackbar
  condivisa vive nello `Scaffold` root (`ui/components/UndoSnackbar.kt`), non una per
  schermata. **Modificare** una spesa esistente mostra "Spesa aggiornata" **senza** Annulla
  (richiederebbe i valori precedenti, già sovrascritti) - copre la richiesta più vecchia
  dell'utente "dovrebbe dirmi se il salvataggio è andato a buon fine".
- **Animazioni**: `Modifier.animateItem()` (non `animateItemPlacement`, deprecata/rimossa
  nella Compose Foundation di questo progetto) su tutte le liste con `key` stabile -
  Storico, Home (categorie e spese), Statistiche, Gruppo (feed attività), Ricorrenti,
  Categorie (visibile soprattutto lì: le frecce di riordino ora animano lo scambio invece
  di scattare). `AnimatedContent` sul totale di `MonthSummaryCard` e sul testo del saldo
  (Home e Gruppo). Haptic (`HapticFeedbackType.LongPress`) al salvataggio riuscito di una
  spesa in `AddExpenseSheet`.
- **Accessibilità**: audit di tutte le 16 icone con `contentDescription = null` nel
  codebase - risultato pulito, sono tutte decorative (affiancate da testo visibile che
  dice la stessa cosa: pulsanti con icona+etichetta, voci di menu, chip). Nessun
  `IconButton` autonomo (l'unica azione, nessun testo accanto) ha `contentDescription`
  nullo. **Contrasto verificato per davvero**, non a vista: calcolo del rapporto WCAG
  (formula di luminanza relativa) per tutte le 14 coppie testo/sfondo di
  `ui/theme/Semantic.kt` in chiaro e scuro. Trovato un problema reale (non solo teorico):
  `onPositive` (bianco) su `positive` (`SeaGreen`, lo stesso verde di `colorScheme.tertiary`)
  arriva solo a 4.06:1, sotto la soglia AA di 4.5:1 per testo normale - però quella coppia
  **non è ancora usata da nessuno schermo** (solo `positive` da solo, come colore di testo
  su sfondo chiaro, già ampiamente sopra soglia). Corretto senza toccare `SeaGreen` (che è
  anche `colorScheme.tertiary`, già visibile altrove): nuova costante `SeaGreenDeep`
  (`Color.kt`) usata solo per questa coppia in `Semantic.kt`, 4.60:1. Le altre 13 coppie
  erano già tutte sopra soglia AA (minimo 4.95:1).
- **Widget home screen "Speso questo mese": non implementato**, come da nota "opzionale,
  ultima cosa" del piano. È una feature a sé (provider, layout RemoteViews, trigger di
  aggiornamento, voce in `AndroidManifest.xml`), non una rifinitura da 10 righe come le
  altre di questa fase - va fatta come lavoro dedicato se e quando serve, non infilata qui.

## 4. Fasi, ordine e modello consigliato

Ogni fase deve chiudersi con `assembleDebug` verde e un APK consegnato all'utente.
Non mischiare fasi in un solo commit.

| Fase | Contenuto | Tocca | Modello |
|---|---|---|---|
| 0 | ✅ Fatta (2026-09-13). Design system + componenti condivisi (3.2), deduplica `ExpenseRow`/sheet, aggiunta Vico. Vedi 3.2 per cosa è cambiato rispetto alla proposta. | `ui/theme`, `ui/components`, Dashboard/Recurring/Categories per usare i componenti | **Sonnet** |
| 1 | ✅ Fatta (2026-09-13). Nuova navigazione (3.1): tab Home/Storico/Statistiche/Altro + FAB centrale; Home ridotta; Storico con raggruppamento per giorno (senza filtri); Ricorrenti/Categorie sotto Altro. Vedi 3.1 per cosa è cambiato rispetto alla proposta. | `MainActivity`, `navigation/Screen.kt`, nuovi `ui/home`, `ui/history`, `ui/stats`, `ui/more`, `ui/month` (ex `ui/dashboard`, rimossa) | **Opus** (trasversale, richiede giudizio) |
| 2 | ✅ Fatta (2026-09-13). Quick-add (3.3) + `lastUsedCategoryId` + "Duplica". Vedi 3.3 per cosa è cambiato rispetto alla proposta. | `ui/addexpense`, `SettingsRepository`, nuovo `ui/components/AmountKeypad`, `DetailSheet` | **Opus** se il budget lo consente, altrimenti Sonnet con questo doc |
| 3 | ✅ Fatta (2026-09-14). Storico: ricerca e filtri (3.4). Vedi 3.4 per cosa è cambiato rispetto alla proposta. | `ui/history` (nuovo `HistoryFilters.kt`) | **Sonnet** |
| 4 | ✅ Fatta (2026-09-14). Statistiche (3.5). Vedi 3.5 per cosa è cambiato rispetto alla proposta. | `ui/stats` (nuovo), `ui/month/MonthViewModel`, `ui/components/CategoryDonutChart` | **Sonnet** |
| 5 | ✅ Fatta (2026-09-14). Coppia/Gruppo (3.6). Vedi 3.6 per cosa è cambiato rispetto alla proposta. | nuovo `ui/group`, `Screen.kt`, `MoreScreen`, `HomeScreen`, `SettingsScreen`/`SettingsViewModel` (sezione Gruppo rimossa, spostata) | **Sonnet** |
| 6 | ✅ Fatta (2026-09-14). Rifiniture (3.7) - widget home screen non incluso (vedi 3.7). | trasversale | **Sonnet** |

Perché così: la Fase 1 ridisegna l'ossatura e ogni scelta lì condiziona il resto, quindi
vale il modello più forte; dalla Fase 3 in poi il lavoro è ben delimitato da questo
documento e Sonnet è sufficiente ed economico. La Fase 0 prima della 1 evita di riscrivere
due volte le righe lista.

## 5. Criteri di accettazione (per l'utente, da provare sul telefono)

- Inserire una spesa "importo + categoria" richiede al massimo 3 tap e nessuno scroll.
- Home entra in una schermata senza scroll su un telefono da 6".
- In Storico trovo una spesa per nota in ≤ 2 s e vedo il totale di ogni giorno.
- In Statistiche capisco se sto spendendo più del mese scorso e in quale categoria.
- Nella sezione Coppia capisco perché il saldo è quello che è (cosa ha pagato chi).
- Nessuna regressione su: valute (EUR primario + originale), condivisa off di default,
  guardia anti-perdita dati, login Google, riordino categorie, ricorrenti generate.
- Build verde, entrambe le lingue complete, APK installabile sopra il precedente.

## 6. Cose da NON fare

- Non cambiare lo schema Firestore né le regole (`firestore.rules`).
- Non riattivare dynamic color: la palette è un tratto distintivo voluto.
- Non introdurre librerie oltre Vico senza chiederlo.
- Non toccare `google-services.json`, `local.properties`, il keystore debug.
- Non fondere le categorie tra i due utenti a livello dati: la corrispondenza è solo per
  nome in lettura (`CategoryResolver`), le liste restano private.

## 7. Affidabilità offline/lag (post-piano, 2026-09-16)

Non una fase del redesign - correzioni di affidabilità dopo che l'uso reale ha mostrato
il problema: salvare una spesa senza connessione non dava feedback, l'utente ha premuto
"Salva" più volte pensando non avesse funzionato, e al ritorno online sono comparse 3
copie della stessa spesa. Anche l'eliminazione sembrava "non fare niente" pur mostrando
il messaggio di conferma.

- **Causa della spesa triplicata**: `AddExpenseViewModel.saveExpense` non aveva nessuna
  guardia contro tap ripetuti - ogni tap lanciava una scrittura Firestore indipendente, e
  senza connessione lo `.await()` su quella scrittura può restare in sospeso a lungo (il
  dato è già in coda nella cache locale, ma la Task non si conclude finché il server non
  conferma). Aggiunta `AddExpenseViewModel.isSaving` (blocca i tap ripetuti, disabilita
  il pulsante Salva con spinner) e `withTimeoutOrNull(8s)` attorno alla scrittura: allo
  scadere si procede comunque come se fosse andata a buon fine (il dato è già in cache
  locale - riprovare avrebbe ricreato lo stesso problema), semplicemente senza l'id per
  offrire "Annulla" su quella spesa specifica.
- **Eliminazione resa onesta**: `MonthViewModel`/`GroupViewModel.deleteExpense` sono ora
  `suspend fun` che restituiscono `WriteOutcome` (SUCCESS/TIMED_OUT/FAILED) invece di
  lanciare-e-dimenticare. Lo snackbar "Spesa eliminata" con Annulla appare solo dopo aver
  atteso l'esito reale (con lo stesso timeout di 8s - un timeout conta come successo, un
  errore vero no); un fallimento genuino mostra "Non sono riuscito a eliminare la spesa -
  riprova" invece di mentire.
- **Trovato un bug concreto separato investigando**: `RecurringExpenseGenerator` scriveva
  ogni spesa generata e poi l'avanzamento di `lastGeneratedDate` sul template come
  operazioni indipendenti. Se il processo veniva interrotto tra le due (lag, offline, app
  chiusa a metà sync), al riavvio il `lastGeneratedDate` risultava ancora vecchio e la
  stessa data veniva rigenerata - con lo stesso id deterministico (`insertGeneratedExpense`
  usa `"<recurringId>_<data>"`), risorgendo silenziosamente una spesa che l'utente aveva
  nel frattempo cancellato o modificato. Ora `ExpenseRepository.insertGeneratedExpensesAndAdvance`
  scrive tutte le occorrenze dovute + l'avanzamento della data in un unico `WriteBatch`
  atomico.
- **Banner offline persistente**: nuovo `data/ConnectivityObserver` (Android
  `ConnectivityManager`, capacità `NET_CAPABILITY_VALIDATED` - non solo "c'è un
  adattatore di rete", ma "Android ha verificato che c'è davvero internet"). Firestore di
  suo non segnala mai "sono offline" (mette in coda e sincronizza in silenzio, per
  design) - questo è il segnale che mancava. Mostrato come barra persistente sotto la
  barra superiore (non uno snackbar, che sparirebbe da solo mentre l'offline può durare a
  lungo), in `MainActivity`.
- **Pull-to-refresh in Home**: `PullToRefreshBox` (Material3). Tutto è già live via
  listener Firestore, quindi non c'è "nuovo" da scaricare - il gesto è comunque quello
  che la gente usa istintivamente quando qualcosa sembra fermo, quindi è collegato a un
  segnale vero (`MonthViewModel.refresh` → `waitForPendingWrites()` con lo stesso
  timeout) invece di girare a vuoto per un tempo fisso.
- **Colore per pagatore nel feed di Gruppo**: `ExpenseRow` accetta ora un `accentColor`
  opzionale (striscia di 4dp sul bordo sinistro, ritagliata sugli angoli arrotondati della
  card) - `null` ovunque tranne `GroupScreen`, che colora ogni spesa condivisa con
  `colorScheme.primary` (tu) o `colorScheme.tertiary` (partner), gli stessi due colori di
  marchio già usati altrove, non nuovi. Piccola legenda a pallini sopra il feed.
- **Correzione (2026-09-17, feedback utente)**: due problemi nel giro precedente.
  1. Lo spinner del pull-to-refresh spariva troppo in fretta per essere notato - quando
     non c'è nulla in sospeso `waitForPendingWrites()` si risolve in pochi millisecondi,
     quindi lo spinner lampeggiava invece di girare. Aggiunta una durata minima visibile
     di 600 ms in `HomeScreen`, eseguita in parallelo al controllo vero (non in coda dopo)
     così un refresh lento/offline non viene rallentato ulteriormente, solo uno
     sospettosamente veloce viene "trattenuto" fino al minimo.
  2. `colorScheme.primary` (FjordBlue) e `colorScheme.tertiary` (SeaGreen) sono entrambi
     blu-verde/teal e su una striscia sottile di 4dp risultavano quasi indistinguibili.
     Sostituito `tertiary` con `colorScheme.secondary` (CoralAccent, lo stesso arancione
     già usato per il FAB "+") - molto più lontano da `primary` sulla ruota dei colori,
     esattamente ciò che serve per distinguere due persone a colpo d'occhio.

## Fonti consultate

- [Monarch vs YNAB (Monarch)](https://www.monarch.com/compare/ynab-alternative)
- [Best Expense Tracker Apps of 2026 (KeenPocket)](https://keenpocket.com/best-expense-tracker-apps/)
- [Best Budgeting Apps of 2026 (Forbes Advisor)](https://www.forbes.com/advisor/banking/best-budgeting-apps/)
- [Best Money Tracker App in 2026 (Finny)](https://getfinny.app/blog/best-money-tracker-app-in-2026)
- [Tricount vs Splitwise 2026 (SplitPilot)](https://splitpilot.io/blog/tricount-vs-splitwise/)
- [7 Best Expense Splitting Apps in 2026 (HippoSplit)](https://hipposplit.com/blog/best-expense-splitting-apps/)
- [Splitwise vs Tricount vs Spllito (Spllito)](https://spllito.com/blog/splitwise-vs-tricount-vs-spllito)
- [Finance App Design Strategies for 2026 (ProCreator)](https://procreator.design/blog/finance-app-design-best-practices/)
- [Personal Finance Apps: What Users Expect in 2026 (WildnetEdge)](https://www.wildnetedge.com/blogs/personal-finance-apps-what-users-expect)
- [15 Best Finance App Designs in 2026 (Gummble)](https://gummble.com/blog/best-finance-app-designs-2026)
