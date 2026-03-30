create table if not exists public.device_kiosk_modes (
    device_id text not null,
    package_name text not null,
    is_kiosk boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (device_id, package_name)
);

create unique index if not exists device_kiosk_modes_one_kiosk_per_device
    on public.device_kiosk_modes (device_id)
    where is_kiosk;

alter table public.device_kiosk_modes enable row level security;

drop policy if exists "anon_select_device_kiosk_modes" on public.device_kiosk_modes;
create policy "anon_select_device_kiosk_modes"
    on public.device_kiosk_modes
    for select
    to anon
    using (true);

drop policy if exists "anon_insert_device_kiosk_modes" on public.device_kiosk_modes;
create policy "anon_insert_device_kiosk_modes"
    on public.device_kiosk_modes
    for insert
    to anon
    with check (true);

drop policy if exists "anon_update_device_kiosk_modes" on public.device_kiosk_modes;
create policy "anon_update_device_kiosk_modes"
    on public.device_kiosk_modes
    for update
    to anon
    using (true)
    with check (true);

drop policy if exists "anon_delete_device_kiosk_modes" on public.device_kiosk_modes;
create policy "anon_delete_device_kiosk_modes"
    on public.device_kiosk_modes
    for delete
    to anon
    using (true);
