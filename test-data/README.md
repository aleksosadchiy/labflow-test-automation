# test-data

The one place test data comes from. Both test modules use it, so a patient or a sample is described the same way in an API check and in a screen check.

- `Patients`, `Orders` and `Samples` are recipes: each returns a plain draft record (`PatientDraft`, `OrderDraft`, `SampleDraft`) with valid defaults, plus named variations for refusal checks, such as a draft without a name.
- `UniqueData.name(kind)` gives `TEST-<kind>-<uuid>`. The `TEST-` prefix marks the record as made by the framework, and the UUID keeps it unique when checks run side by side.

A recipe never calls the lab: it only builds values. The check passes them to a service in `api-client`. That keeps this module free of HTTP, and it depends on `common` only.

**Run its tests** from the repository root. They need no lab:

```bash
./mvnw -pl test-data -am test
```

`-am` ("also make") builds `common` in the same run. More in the [root README](../README.md).
