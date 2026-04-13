# Multi-Project Support (Option C: URL-Based Routing)

## Completed

- [x] Step 1-2: ProjectEntry model + LocalStorageKeys
- [x] Step 3-4: ProjectIndex + ProjectStorage
- [x] Step 5: ProjectMigration
- [x] Step 6: Update Router with new Page types
- [x] Step 7: ProjectManager
- [x] Steps 8-9: Update Frontend.scala + Variables.scala
- [x] Step 10: ProjectSelectorView
- [x] Steps 11-13: Wire up renderPage, NavBar, ProjectComponent
- [x] Step 14: i18n strings
- [x] Compile and verify (ui module compiles clean)

## Verification Checklist

- [ ] Fresh load: clear localStorage, visit /fr -> project selector
- [ ] Create project: click "Nouveau projet" -> redirects to /fr/project/{id}
- [ ] Migration: set old app_state_schema -> visit / -> auto-migrate
- [ ] Multi-tab: open /fr/project/A and /fr/project/B in separate tabs
- [ ] Open file: import .fcalc -> creates new project + navigates
- [ ] 404: navigate to /fr/project/nonexistent -> redirects to selector
- [ ] Language switch: navbar lang/unit buttons preserve project ID in URL
