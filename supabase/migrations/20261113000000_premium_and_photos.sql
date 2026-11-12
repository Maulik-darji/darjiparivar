-- Premium (₹99/month) gating, multiple photos, and removal of the diet field.
-- Run after 20261112000000_init.sql. Safe to run once on the existing project.

-- ---------------------------------------------------------------------------
-- 1. Premium-only profile details move to their own table
-- ---------------------------------------------------------------------------

create table public.profile_details (
  profile_id uuid primary key references public.profiles on delete cascade,
  about      text check (char_length(about) <= 1000),
  income     text,
  family     text
);

insert into public.profile_details (profile_id, about, income, family)
select id, about, income, family from public.profiles
on conflict (profile_id) do nothing;

alter table public.profiles
  drop column about,
  drop column income,
  drop column family,
  drop column if exists diet,
  add column premium_until timestamptz;

-- ---------------------------------------------------------------------------
-- 2. Subscriptions (written only by the verify-subscription Edge Function)
-- ---------------------------------------------------------------------------

create table public.subscriptions (
  purchase_token text primary key,
  user_id        uuid not null references auth.users on delete cascade,
  product_id     text not null,
  state          text not null,
  expires_at     timestamptz,
  updated_at     timestamptz not null default now()
);

create or replace function public.is_subscribed(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = uid and premium_until > now());
$$;

-- Members can't grant themselves premium.
create or replace function public.protect_profile_flags() returns trigger
language plpgsql as $$
begin
  if current_user = 'authenticated' then
    if tg_op = 'INSERT' then
      new.verified := false;
      new.premium := false;
      new.premium_until := null;
    else
      new.verified := old.verified;
      new.premium := old.premium;
      new.premium_until := old.premium_until;
    end if;
  end if;
  return new;
end $$;

-- ---------------------------------------------------------------------------
-- 3. Photos: up to 6 per member, one main photo
-- ---------------------------------------------------------------------------

create unique index if not exists photos_one_main on public.photos (owner_id) where is_primary;

create or replace function public.enforce_photo_limit() returns trigger
language plpgsql as $$
begin
  if (select count(*) from public.photos where owner_id = new.owner_id) >= 6 then
    raise exception 'not_allowed: photo limit is 6';
  end if;
  return new;
end $$;
create trigger photos_limit before insert on public.photos
  for each row execute function public.enforce_photo_limit();

-- A photo file is visible to its owner; the main photo to everyone allowed to see photos;
-- the rest of the gallery only to subscribers.
create or replace function public.can_view_photo_object(object_name text) returns boolean
language sql stable security definer set search_path = public as $$
  select (storage.foldername(object_name))[1] = auth.uid()::text
      or exists (select 1 from photos p
                 where p.path = object_name
                   and can_view_photo(p.owner_id)
                   and (p.is_primary or is_subscribed(auth.uid())));
$$;

-- ---------------------------------------------------------------------------
-- 4. Row Level Security
-- ---------------------------------------------------------------------------

alter table public.profile_details enable row level security;
alter table public.subscriptions   enable row level security;

create policy "details: owner or subscriber" on public.profile_details for select to authenticated
  using (profile_id = auth.uid()
         or (is_subscribed(auth.uid()) and not is_blocked(profile_id, auth.uid())));
create policy "details: write own" on public.profile_details for all to authenticated
  using (profile_id = auth.uid()) with check (profile_id = auth.uid());

create policy "subscriptions: read own" on public.subscriptions for select to authenticated
  using (user_id = auth.uid());

-- Translations of premium fields need a subscription too.
drop policy "translations: read" on public.profile_translations;
create policy "translations: read" on public.profile_translations for select to authenticated
  using (not is_blocked(profile_id, auth.uid())
         and (field not in ('about', 'income', 'family')
              or profile_id = auth.uid() or is_subscribed(auth.uid())));

-- Contact details: connected AND subscribed.
drop policy "contacts: owner or connection" on public.profile_contacts;
create policy "contacts: owner or subscribed connection" on public.profile_contacts for select to authenticated
  using (profile_id = auth.uid()
         or (is_connected(profile_id, auth.uid()) and not is_blocked(profile_id, auth.uid())
             and is_subscribed(auth.uid())));

drop policy "photos: read if allowed" on public.photos;
create policy "photos: read if allowed" on public.photos for select to authenticated
  using (owner_id = auth.uid()
         or (can_view_photo(owner_id) and (is_primary or is_subscribed(auth.uid()))));

drop policy "photos bucket: read if allowed" on storage.objects;
create policy "photos bucket: read if allowed" on storage.objects for select to authenticated
  using (bucket_id = 'photos' and can_view_photo_object(name));
