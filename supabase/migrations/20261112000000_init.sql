-- Darji Parivar schema. Apply with `supabase db push` or paste into the SQL editor.
-- Every table has Row Level Security on; clients only ever use the anon key + user JWT.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.profiles (
  id            uuid primary key references auth.users on delete cascade,
  display_id    text unique not null default ('DP' || lpad((floor(random() * 90000) + 10000)::int::text, 5, '0')),
  name          text not null check (char_length(name) between 2 and 60),
  gender        text not null check (gender in ('M', 'F')),
  age           int  not null check (age between 18 and 80),
  height        text,
  city          text,
  state         text,
  community     text,
  education     text,
  profession    text,
  income        text,
  diet          text,
  family        text,
  about         text check (char_length(about) <= 1000),
  pref_age_min  int  not null default 18 check (pref_age_min between 18 and 80),
  pref_age_max  int  not null default 80 check (pref_age_max between 18 and 80),
  photo_path    text,
  photo_visibility text not null default 'all' check (photo_visibility in ('all', 'connections')),
  verified      boolean not null default false,  -- set by moderators only
  premium       boolean not null default false,  -- set by billing only
  last_seen     timestamptz not null default now(),
  created_at    timestamptz not null default now()
);
create index profiles_discover_idx on public.profiles (gender, age, last_seen desc);

-- Gujarati (or other) versions of profile fields, one row per field.
create table public.profile_translations (
  profile_id uuid not null references public.profiles on delete cascade,
  lang       text not null check (lang in ('en', 'gu')),
  field      text not null check (field in ('name','city','state','community','education',
                                            'profession','income','diet','family','about')),
  value      text not null,
  primary key (profile_id, lang, field)
);

-- Private contact details, only readable by the owner and accepted connections.
create table public.profile_contacts (
  profile_id uuid primary key references public.profiles on delete cascade,
  phone      text,
  email      text
);

create table public.photos (
  id         uuid primary key default gen_random_uuid(),
  owner_id   uuid not null references public.profiles on delete cascade,
  path       text not null,
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  unique (owner_id, path)
);

create table public.interests (
  id          uuid primary key default gen_random_uuid(),
  sender_id   uuid not null references public.profiles on delete cascade,
  receiver_id uuid not null references public.profiles on delete cascade,
  status      text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at  timestamptz not null default now(),
  responded_at timestamptz,
  unique (sender_id, receiver_id),
  check (sender_id <> receiver_id)
);
create index interests_receiver_idx on public.interests (receiver_id, status);
create index interests_sender_day_idx on public.interests (sender_id, created_at);

create table public.shortlists (
  owner_id   uuid not null references auth.users on delete cascade,
  profile_id uuid not null references public.profiles on delete cascade,
  created_at timestamptz not null default now(),
  primary key (owner_id, profile_id)
);

create table public.blocks (
  blocker_id uuid not null references auth.users on delete cascade,
  blocked_id uuid not null references public.profiles on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id)
);

create table public.reports (
  id          uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users on delete cascade,
  reported_id uuid not null references public.profiles on delete cascade,
  reason      text not null,
  details     text check (char_length(details) <= 2000),
  created_at  timestamptz not null default now()
);

create table public.conversations (
  id         uuid primary key default gen_random_uuid(),
  user_a     uuid not null references public.profiles on delete cascade,
  user_b     uuid not null references public.profiles on delete cascade,
  created_at timestamptz not null default now(),
  check (user_a < user_b),
  unique (user_a, user_b)
);

create table public.messages (
  id              bigint generated always as identity primary key,
  conversation_id uuid not null references public.conversations on delete cascade,
  sender_id       uuid not null references public.profiles on delete cascade,
  body            text not null check (char_length(body) between 1 and 2000),
  created_at      timestamptz not null default now()
);
create index messages_conversation_idx on public.messages (conversation_id, created_at);

-- ---------------------------------------------------------------------------
-- Helper functions (security definer so policies can look across tables)
-- ---------------------------------------------------------------------------

create or replace function public.is_blocked(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from blocks
                 where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a));
$$;

create or replace function public.is_connected(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from interests
                 where status = 'accepted'
                   and ((sender_id = a and receiver_id = b) or (sender_id = b and receiver_id = a)));
$$;

create or replace function public.can_view_photo(owner uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select owner = auth.uid()
      or (not is_blocked(owner, auth.uid())
          and exists (select 1 from profiles p where p.id = owner
                      and (p.photo_visibility = 'all' or is_connected(owner, auth.uid()))));
$$;

create or replace function public.is_participant(conv uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from conversations c
                 where c.id = conv and auth.uid() in (c.user_a, c.user_b)
                   and not is_blocked(c.user_a, c.user_b));
$$;

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

-- Rate-limited interest sending: 20 per rolling 24 hours.
create or replace function public.send_interest(target uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  sent_today int;
begin
  if me is null then raise exception 'not_allowed: sign in first'; end if;
  if me = target then raise exception 'not_allowed: cannot send interest to yourself'; end if;
  if is_blocked(me, target) then raise exception 'not_allowed: blocked'; end if;
  select count(*) into sent_today from interests
   where sender_id = me and created_at > now() - interval '24 hours';
  if sent_today >= 20 then raise exception 'daily_limit: 20 interests per day'; end if;
  insert into interests (sender_id, receiver_id) values (me, target)
  on conflict (sender_id, receiver_id) do nothing;
end $$;

-- Accept or decline an interest; accepting opens a conversation.
create or replace function public.respond_interest(sender uuid, accept boolean) returns void
language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid();
begin
  update interests set status = case when accept then 'accepted' else 'declined' end,
                       responded_at = now()
   where sender_id = sender and receiver_id = me and status = 'pending';
  if not found then raise exception 'not_found: no pending interest'; end if;
  if accept then
    insert into conversations (user_a, user_b)
    values (least(me, sender), greatest(me, sender))
    on conflict (user_a, user_b) do nothing;
  end if;
end $$;

-- Deletes the caller's auth user; everything else cascades. The app deletes the photo
-- through the Storage API first (Storage objects can't be deleted from SQL).
create or replace function public.delete_account() returns void
language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null then raise exception 'not_allowed'; end if;
  delete from auth.users where id = auth.uid();
end $$;

revoke all on function public.delete_account() from anon;
revoke all on function public.send_interest(uuid) from anon;
revoke all on function public.respond_interest(uuid, boolean) from anon;

-- Keep last_seen fresh whenever the user writes their profile.
create or replace function public.touch_last_seen() returns trigger
language plpgsql as $$ begin new.last_seen := now(); return new; end $$;
create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_last_seen();

-- Users can't promote themselves.
create or replace function public.protect_profile_flags() returns trigger
language plpgsql as $$
begin
  if current_user = 'authenticated' then
    new.verified := coalesce(old.verified, false);
    new.premium  := coalesce(old.premium, false);
  end if;
  return new;
end $$;
create trigger profiles_protect before insert or update on public.profiles
  for each row execute function public.protect_profile_flags();

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------

alter table public.profiles             enable row level security;
alter table public.profile_translations enable row level security;
alter table public.profile_contacts     enable row level security;
alter table public.photos               enable row level security;
alter table public.interests            enable row level security;
alter table public.shortlists           enable row level security;
alter table public.blocks               enable row level security;
alter table public.reports              enable row level security;
alter table public.conversations        enable row level security;
alter table public.messages             enable row level security;

-- Profiles: signed-in members see everyone except people in a block with them.
create policy "profiles: read unblocked" on public.profiles for select to authenticated
  using (id = auth.uid() or not is_blocked(id, auth.uid()));
create policy "profiles: insert own" on public.profiles for insert to authenticated
  with check (id = auth.uid());
create policy "profiles: update own" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy "translations: read" on public.profile_translations for select to authenticated
  using (not is_blocked(profile_id, auth.uid()));
create policy "translations: write own" on public.profile_translations for all to authenticated
  using (profile_id = auth.uid()) with check (profile_id = auth.uid());

-- Contacts: owner, or a member they're connected with.
create policy "contacts: owner or connection" on public.profile_contacts for select to authenticated
  using (profile_id = auth.uid() or (is_connected(profile_id, auth.uid()) and not is_blocked(profile_id, auth.uid())));
create policy "contacts: write own" on public.profile_contacts for all to authenticated
  using (profile_id = auth.uid()) with check (profile_id = auth.uid());

create policy "photos: read if allowed" on public.photos for select to authenticated
  using (can_view_photo(owner_id));
create policy "photos: write own" on public.photos for all to authenticated
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- Interests: visible to both sides; created and answered only through the RPCs above.
create policy "interests: participants read" on public.interests for select to authenticated
  using (auth.uid() in (sender_id, receiver_id));

create policy "shortlists: own" on public.shortlists for all to authenticated
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy "blocks: own" on public.blocks for all to authenticated
  using (blocker_id = auth.uid()) with check (blocker_id = auth.uid());

-- Reports: write-only for members; moderators read them with the service role.
create policy "reports: insert own" on public.reports for insert to authenticated
  with check (reporter_id = auth.uid());

create policy "conversations: participants" on public.conversations for select to authenticated
  using (auth.uid() in (user_a, user_b));

create policy "messages: participants read" on public.messages for select to authenticated
  using (is_participant(conversation_id));
create policy "messages: participants send" on public.messages for insert to authenticated
  with check (sender_id = auth.uid() and is_participant(conversation_id));

-- Realtime: stream new messages (RLS above still applies to subscribers).
alter publication supabase_realtime add table public.messages;

-- ---------------------------------------------------------------------------
-- Storage: private "photos" bucket, files at <user id>/avatar.jpg
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', false, 5242880, array['image/jpeg'])
on conflict (id) do nothing;

create policy "photos bucket: read if allowed" on storage.objects for select to authenticated
  using (bucket_id = 'photos' and can_view_photo(((storage.foldername(name))[1])::uuid));
create policy "photos bucket: upload own" on storage.objects for insert to authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "photos bucket: update own" on storage.objects for update to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "photos bucket: delete own" on storage.objects for delete to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
