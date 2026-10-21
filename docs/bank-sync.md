# Step 8: bank sync (optional)

Fetches booked transactions straight from the bank, as a complement to file import. Built for
one person's own accounts first: free, kept apart from the rest of the app, and easy to move to
another provider.

## Provider

| Provider | Free for own accounts | Swedish banks | Notes |
|---|---|---|---|
| **Enable Banking** (chosen) | Yes: "restricted production" only reaches accounts you link to your own application | Länsförsäkringar, Swedbank, SEB, Handelsbanken, Nordea, ICA, Skandia, Avanza and others | Plain REST + a JWT signed by the app's own RSA key; no backend needed |
| GoCardless Bank Account Data (Nordigen) | Was free | Yes | Closed to new sign-ups since July 2025 |
| Tink (Visa) | No | Yes | Enterprise contracts |
| Open Banking Sweden direct (PSD2 per bank) | — | Per bank | Needs a TPP licence and eIDAS certificates |

Enable Banking's restricted mode is the only free option that reaches real Swedish accounts in
production. Reaching other people's accounts needs a paid contract with any provider, so the code
keeps the provider behind one interface.

## Isolation

- **Modules.** Everything lives in `:feature:banksync:*`:
  - `domain`: provider-neutral models, the `BankSyncProvider` interface and the sync use case (pure JVM).
  - `enablebanking`: the only module that talks to the network; Ktor + RS256 JWT.
  - `data`: credential store (Android Keystore) and the link store.
  - `ui`: Profil → Bankkoppling.

  The rest of the app sees only rows arriving through the same pipeline as file import.
- **Off by default.** Nothing connects until you import your own key and link a bank. It is only
  offered in "Mina" mode; demo and the empty app never show it.
- **Network.** The app gains the INTERNET permission for this one feature. The HTTP client only
  talks to `api.enablebanking.com` over HTTPS (cleartext is off app-wide), and logs no bodies.
- **Secrets.**
  - The private key (.pem) and application id are yours, created in Enable Banking's control panel.
  - The app reads the key once from a file you pick, then encrypts it with an AES-GCM key held in
    the Android Keystore. It is stored in `noBackupFilesDir`, so it is excluded from backup and
    device transfer.
  - The key is never written to the repo, logs or settings. "Ta bort nyckel" removes it.
- **Read-only.** Only the account endpoints are used (`/application` to check the key, `/aspsps`,
  `/auth`, `/sessions`, transactions). There is no payment code.

## Flow

1. **Nyckel**: pick the `.pem` file, paste the application id and the redirect address. "Kontrollera
   och spara" calls `GET /application` first: the key must sign, the application must be active and
   the redirect address must be one of its registered ones.
2. **Koppla bank**:
   - Choose the bank from `GET /aspsps?country=SE`.
   - `POST /auth` with access `valid_until` (up to 180 days) and a random `state`.
   - The bank's BankID page opens in the browser.
3. **Tillbaka till appen**: the redirect URL carries `code` and `state`.
   - The control panel only accepts https, so the redirect URL is
     `https://mattiasback.github.io/cashflow-callback`: a copy of `docs/bank-callback/index.html`
     in the mattiasback.github.io repo. It only forwards `?code&state` to `cashflow://bank-callback`,
     which the app's intent filter catches.
   - **Fallback, always available:** paste the address from the browser.
   - Either way the app checks `state`, then calls `POST /sessions`.
4. **Konton**:
   - Each bank account is mapped once to an existing CashFlow account (matched by the account
     number when known), or to a new one with a type.
   - The mapping is keyed by `identification_hash`, which stays the same across sessions, so
     renewing consent does not ask again.
5. **Synka nu**:
   - Fetch transactions from the start date to today, booked only (`transaction_status=BOOK`).
   - Page through `continuation_key`, map the rows and reconcile them (see below). A row's
     `balance_after_transaction`, when the bank gives one, becomes a known balance for the account.
   - Insert, then match transfers and categorize as usual.
   - Show a receipt: how many rows are new and how many were already there from a file.
6. **Samtycke**: shows the date the consent is valid until. "Koppla från" deletes the session at
   the provider and keeps the rows.

Sync is manual ("Synka nu") in this step. A background sync can come later, once the manual one
has been tried on real accounts.

## Duplicates between file import and sync

The same bank event can arrive twice, with different text and no shared id:

- A Länsförsäkringar xls row: `Kortköp 261012 ICA …`, with a balance.
- The API row: `remittance_information` `ICA …`, an `entry_reference`, maybe no balance.

The rules:

1. **Each row has an origin**: `FILE` or `BANK`. Stable ids within an origin stay as they are.
   - File rows: sha256 of the raw row, as today.
   - Bank rows: sha256 of `provider | identification_hash | entry_reference` (or `transaction_id`).
     When the bank gives neither, the hash uses date, amount, text and a sequence number per
     whole day.
   - A repeated sync, or a repeated file import, is skipped by id, as today.
2. **Cross-origin matching** (`SourceMatcher` in `:core:import-engine`), run before insert in both
   directions: sync after file, and file after sync.
   - A pair must have:
     - the same account;
     - the same amount in öre;
     - a booking date at most 3 days apart.
   - Candidate pairs are ranked by booking-date distance, then the same payee, then
     transaction-date distance.
   - Pairs are assigned 1:1 for the whole batch, so two genuine 45 kr coffees on one day pair with
     two rows, never one.
3. **A match keeps the row that is already there**, with its category, notes and review decisions.
   The incoming id is stored as an **alias** (`transaction_aliases`), so the next sync or import
   skips it by id.
   - A file's balances are still stored as known balances, so the balance chain keeps working.
4. **When a match is unclear, ask instead of guessing.** An incoming row with no 1:1 partner, but
   with an other-origin row of the same amount within 3 days that is already taken, is inserted
   and flagged "Möjlig dubblett" in the existing review. The answers are the existing ones: "Ta
   bort" (its id is tombstoned, so neither origin brings it back) or "Inte en dubblett".
5. **Same-origin duplicate detection is unchanged for files.** Bank rows are left out of
   `DuplicateDetector`, since their ids are stable and two coffees a day apart are not duplicates.
6. **Pending rows** (`PDNG`, `HOLD`) are never stored; they would be replaced when booked, under
   another reference.
7. **Start date of the first sync** per account: the day after the last file-imported row, minus
   7 days of overlap (matching handles the overlap). With no file rows, it starts as far back as
   the bank allows (`strategy=longest`).
   - **Later syncs** start from the last synced booking date minus 7 days.
   - **A later file import** that overlaps synced days is matched as in point 2.
8. **Undo and delete still work.** Undoing a sync batch sets its rows aside like a file import.
   Deleting a row tombstones its id and its aliases, so neither origin brings it back.

The texts differ between origins, so merchant memory and rules keyed on the payee may not hit for
synced rows. The first real sync shows how far apart they are; the mapper is tuned then (see Open
questions).

## Storage (Room v5, additive auto-migration)

- `transactions.origin`: `FILE` by default.
- `transaction_aliases(aliasId PK, txId FK → transactions ON DELETE CASCADE)`.
- `bank_links`:
  - `stableKey` (the provider's `identification_hash`, primary key), `provider`, `accountId`.
  - `institution`, `sessionId`, `providerAccountUid`, `validUntilEpochMillis`, `lastSyncedEpochDay`.
  - `displayName`, `maskedNumber`.
- `ImportSource.BANK_SYNC` for sync batches, so the import history lists them.

## Swapping provider

`BankSyncProvider` is the whole contract:

```kotlin
interface BankSyncProvider {
    val id: String
    suspend fun checkCredentials(credentials: BankCredentials): EmptyResult<BankError>
    suspend fun institutions(country: String): Result<List<BankInstitution>, BankError>
    suspend fun startAuthorization(institution: BankInstitution, validUntil: Instant, state: String): Result<AuthorizationStart, BankError>
    suspend fun completeAuthorization(code: String): Result<BankConsent, BankError>
    suspend fun transactions(providerAccountUid: String, from: LocalDate?, to: LocalDate): Result<List<BankTransaction>, BankError>
    suspend fun revoke(sessionId: String): EmptyResult<BankError>
}
```

A new provider is one new module that implements it and one Koin binding. Reconciliation, storage
and UI stay as they are.

## What you do in Enable Banking

1. Sign up at enablebanking.com and open the Control Panel.
2. Create an application:
   - Environment: **Production**.
   - Let the panel generate the key, and save the `.pem` file.
   - Redirect URL: `https://mattiasback.github.io/cashflow-callback` (the panel refuses
     `cashflow://`).
3. "Activate by linking accounts": link your own Länsförsäkringar accounts with BankID. Only those
   accounts can be reached.
4. Copy the `.pem` to the phone (USB, not a cloud drive), import it in Profil → Bankkoppling, and
   delete the copy.

## Open questions

- **The bank's text format.** How Länsförsäkringar's API text compares with the xls text is only
  known after the first real sync. The mapper is tuned then.

## Temporary: importing your own key (to be replaced before a public release)

Importing the `.pem` and application id by hand is a stand-in for your own use and for technical
testers: every user needs their own free Enable Banking application anyway. For a public release
(step 9) it is replaced by a backend that holds one application's key and signs the calls; the
app then signs in to that backend and users only pick a bank and approve with BankID. The redirect
becomes a verified https App Link.

Everything that belongs to the stand-in is marked `Temporary (step 9)` in the code, so it can be
removed in one go:

- `KeyFileReader` / `ContentKeyFileReader`, `BankKeyUseCase`, `BankCredentials`.
- `KeystoreCredentialStore` (the pending-authorization part moves to a plain store).
- `EnableBankingProvider.checkCredentials` and the JWT signing on the device (`JwtSigner`, `PemKeys`).
- The "Nyckel" section of Profil → Bankkoppling (`KeySection`) and its strings.

The backend becomes a new `BankSyncProvider` module; matching, storage, links and the rest of the
screen stay.
