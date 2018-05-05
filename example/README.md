### Special Instructions for Windows users

This project contains symbolic links. These are not always handled
correctly in git on windows. If you are running windows, you will need
to do one of the following two things.

1. Clone the repo normally, then fix all the symbolic links manually. See
 link below for more information.
2. Open a command prompt with administrative privileges and clone the repo
 using git version >= 2.11.1 and the following command.
```
(run as administrator)
git clone -c core.symlinks=true https://github.com/softwaremill/slick-eventsourcing
```
You can find more info on windows symlinks in git [on this stackoverflow
question](https://stackoverflow.com/a/42137273).

Many will probably find the error before they read this, so we include the
error and a brief description to help search engines lead those with this
problem to this documentation. Here is the main error you may see in the
console:
```
ERROR o.f.c.i.c.DbMigrate - Migration of schema "PUBLIC" to version 1 - events failed! Please restore backups and roll back database and code!
```
The actual exception appears in
```
example/data/slickeventsourcing.trace.db
```
It shows that it was trying to run a file path as sql. The problem is
 created when checking out the repo on windows. The symlink
```
example/src/main/resources/db/migration/V1__events.sql ->
  ../../../../../../core/src/main/resourcees/events_schema.sql
```
after checkout is a file whose contents are literally
```
../../../../../../core/src/main/resourcees/events_schema.sql
```
and this file not a symlink at all.
