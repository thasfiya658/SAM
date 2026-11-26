# University Attendance (local demo)

This project is a single-page HTML application with a configurable, VTU-style
academic structure. It supports academic years, semesters, branches/programs,
sections, subjects and credits; student enrollment; teacher accounts and
subject/section assignments; separate class sessions; and student-specific
attendance records for each session.

## Run locally

Serve this folder over localhost (needed for browser password hashing):

```powershell
py -m http.server 8000
```

Open `http://localhost:8000/`. On first use, create the initial teacher account.
The first teacher is the local administrator and can add academic sections,
subjects, teacher accounts and assignments. Assign that teacher to their own
subject/section too if they will take attendance. Other teachers can sign in
with their account ID and password and see/use only their assignments. Create
students with a PIN and share it with each student privately.

Academic details and the attendance minimum are managed in the **Subjects &
setup** page. The default minimum is 75%, and can be changed there. Multiple
sessions can be created for the same subject, section and date; each session
has its own period/start time and its own attendance records.

## Data migration

The app reads the previous `classroom-attendance-v1` browser data and converts
each student/date record into a session for an `IMPORTED` subject. Existing
browser student PIN credentials and the previous teacher password hash are
retained where available. Imported sessions are labelled **Imported daily
record** because the old format did not store subject, teacher, section or
period.

To migrate `attendance-data.txt`, sign in as the local administrator and use
**Subjects & setup → Import legacy data**. This replaces current class,
section, session and attendance data after confirmation; student credentials
with matching IDs are retained. The legacy desktop app's
`attendance-accounts.txt` format is not imported because it does not contain
the student PIN credentials needed by the web portal. Students without a
retained PIN need one set through **Students → Set / reset PIN**.

Keep a private backup before migrating. **Download data backup** exports the
university structure and attendance as JSON (not login hashes); the CSV action
exports the currently filtered attendance report. Restoring a JSON backup is
not implemented. Browser storage is isolated by browser profile and site
origin, so different devices do not share data.

## Hosting and security limitations

**This is not a secure multi-user attendance service.** There is no backend,
database, server-side authentication, access-control enforcement, audit log,
or shared data store. Teacher and student roles, password hashes, assignments
and records live in editable browser storage. UI role checks are only for the
demo workflow and can be bypassed by a user with browser access. Do not enter
real student records or rely on these accounts for privacy or authorization.

GitHub Pages and other static hosting can serve the interface but cannot
provide shared, secure accounts or enforce access to attendance records. A
production university deployment requires a server-side API and persistent
database, with authentication, authorization on every read/write, secure
session management, validation, backups, audit history and database access
rules. The browser must never receive privileged database credentials. No
backend URL or secret is configured by this project.

`_config.yml` excludes the local attendance, account and report files (and
compiled Java classes) from a standard Jekyll/GitHub Pages build. This is only
a deployment safeguard: if the repository itself is public, those source files
and their history may still be accessible. Remove real records from the public
repository and published artifact; do not rely on Jekyll exclusions to protect
private data. In particular, never publish the local `attendance-data.txt`,
`attendance-accounts.txt` or generated report containing personal data.
Password hashing in the browser is not a substitute for server-side
authentication. Use HTTPS in any hosted environment.

## Existing Java application

`Main.java` and its checked-in `.class` files are a separate desktop Swing
application with a legacy student/date attendance model and local text-file
storage. The university-session workflow is implemented in `index.html`; the
Swing application has not been upgraded to this model and should not be used
as a shared backend.
