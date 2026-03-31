create table if not exists public.control_devices (
    id uuid primary key default gen_random_uuid(),
    device_id text not null unique,
    unit_name text not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.control_commands (
    id uuid primary key default gen_random_uuid(),
    device_id text not null,
    command text not null,
    executed boolean not null default false,
    executed_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists control_commands_device_pending_idx
    on public.control_commands (device_id, created_at desc)
    where executed = false;

create table if not exists public.control_kiosk_modes (
    device_id text not null,
    package_name text not null,
    is_kiosk boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (device_id, package_name)
);

create unique index if not exists control_kiosk_modes_one_kiosk_per_device
    on public.control_kiosk_modes (device_id)
    where is_kiosk;

alter table public.control_devices enable row level security;
alter table public.control_commands enable row level security;
alter table public.control_kiosk_modes enable row level security;

drop policy if exists "anon_select_control_devices" on public.control_devices;
create policy "anon_select_control_devices"
    on public.control_devices
    for select
    to anon
    using (true);

drop policy if exists "anon_insert_control_devices" on public.control_devices;
create policy "anon_insert_control_devices"
    on public.control_devices
    for insert
    to anon
    with check (true);

drop policy if exists "anon_update_control_devices" on public.control_devices;
create policy "anon_update_control_devices"
    on public.control_devices
    for update
    to anon
    using (true)
    with check (true);

drop policy if exists "anon_select_control_commands" on public.control_commands;
create policy "anon_select_control_commands"
    on public.control_commands
    for select
    to anon
    using (true);

drop policy if exists "anon_insert_control_commands" on public.control_commands;
create policy "anon_insert_control_commands"
    on public.control_commands
    for insert
    to anon
    with check (true);

drop policy if exists "anon_update_control_commands" on public.control_commands;
create policy "anon_update_control_commands"
    on public.control_commands
    for update
    to anon
    using (true)
    with check (true);

drop policy if exists "anon_delete_control_commands" on public.control_commands;
create policy "anon_delete_control_commands"
    on public.control_commands
    for delete
    to anon
    using (true);

drop policy if exists "anon_select_control_kiosk_modes" on public.control_kiosk_modes;
create policy "anon_select_control_kiosk_modes"
    on public.control_kiosk_modes
    for select
    to anon
    using (true);

drop policy if exists "anon_insert_control_kiosk_modes" on public.control_kiosk_modes;
create policy "anon_insert_control_kiosk_modes"
    on public.control_kiosk_modes
    for insert
    to anon
    with check (true);

drop policy if exists "anon_update_control_kiosk_modes" on public.control_kiosk_modes;
create policy "anon_update_control_kiosk_modes"
    on public.control_kiosk_modes
    for update
    to anon
    using (true)
    with check (true);

drop policy if exists "anon_delete_control_kiosk_modes" on public.control_kiosk_modes;
create policy "anon_delete_control_kiosk_modes"
    on public.control_kiosk_modes
    for delete
    to anon
    using (true);
