# Play Store checklist and Data safety answers

Review these with whoever is legally responsible for the app before publishing. The privacy
policy text in the app (`privacy_body` in `values/strings.xml` and `values-gu/strings.xml`) is a
starting draft: add your legal entity name, address and a contact email, and host the same text
at a public URL (Play Console requires a URL).

## Store listing

- Category: **Dating**. Content rating questionnaire: the app has user-to-user messaging and
  user-generated photos → answer "yes" to user interaction / sharing.
- Target audience: 18+ only (the app enforces age 18–80 at signup and in the database).
- Languages: English and Gujarati — add a Gujarati store listing too.

## Data safety form

| Data type | Collected | Shared with third parties | Purpose | Optional |
|---|---|---|---|---|
| Name | Yes | No | App functionality | No |
| Email address or phone number | Yes | No | Account management | No (one of them) |
| Photos | Yes | No | App functionality | Yes |
| Other info (age, height, city, education, profession, income, diet, family, about) | Yes | No | App functionality | Partly |
| Messages (in-app) | Yes | No | App functionality | — |
| Crash logs | Yes (release only, if Sentry is set up) | No (processor) | Analytics / crash reporting | — |

- Data is encrypted in transit: **Yes** (HTTPS/WSS only; cleartext traffic is blocked by default).
- Users can request deletion: **Yes** — in-app: Account → Delete account. Play also requires a
  web link for deletion requests; add a simple form or email address and put that URL in the
  Data safety section.
- No ads SDKs, no location, no contacts access.

## Policy items specific to dating / matrimony apps

- [x] Block and report from every profile and chat
- [x] Reports stored for moderator review (`public.reports`)
- [x] Contact details hidden until both sides accept
- [x] Photo visibility control (everyone / connections only)
- [x] Rate limit on interests (20 per day, enforced in the database)
- [x] In-app account deletion
- [ ] A moderator process and response time for reports — decide who handles them
- [ ] Profile/photo verification process for the "Verified" badge
- [ ] Hosted privacy policy URL and terms of service
- [ ] Final application id and upload key (see docs/SETUP.md)
