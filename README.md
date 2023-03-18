# Graph Untangler Plugin

# Integration
This plugin is unpublished but can be added to projects using `includeBuild`

1. Checkout this repository
2. Add to `settings.gradle`
```
includeBuild("/path/to/gradle-graph-untangler")
```

3. Add plugin to root `build.gradle`
```
// build.gradle
plugins {
  id("net.siggijons.gradle.graphuntangler")
}
```

# Usage


## Analyze Module Graph
```
./gradlew analyzeModuleGraph
```

### Known Issues
Does not play well with configuration cache and up-to-date checks appear to be off for the same
reason of not treating the `Project` as an input.

Workaround is to run without configuration cache and re-run tasks 
```
./gradlew analyzeModuleGraph --no-configuration-cache --rerun
```

## Calculate Module Change Freqency
```
./gradlew generateChangeFrequencyFile
```

This command assumes git is being used as SCV and will run `git log` commands via bash to calculate the change frequency for each module in the project.

By default, the command looks at 28 days back from today. This behavior can be overwritten with properties:

```
./gradlew generateChangeFrequencyFile -Pfrequency-start=2021-12-10 -Pfrequency-days=14 --info
```

This will look at 14 days back from 2021-12-10.

Note: the task uses `git log --follow --format=oneline --since=<date>`, so implicitly assumes that the current `HEAD` is at `frequency-start` for the lookback.
The idea is to use support something like this to look back in time:

```
# Analyze Graph like it was on 2021-12-13
git checkout `git rev-list -n 1 --before="2021-12-13 00:00" HEAD`
./gradlew generateChangeFrequencyFile -Pfrequency-start=2021-12-13 -Pfrequency-days=28
./gradlew analyzeModuleGraph
```


See `GenerateChangeFrequencyTask` for more details.

## Owners
**Experimental**

Module owner information can be included in analysis by providing an `owners.yaml` file using the following format:

```yaml
app:
  team: App
  modules:
    - :app
    - :feature:settings
feature:
  team: Feature
  modules:
    - :feature:foryou
    - :feature:interests
    - :feature:bookmarks
    - :feature:topic
core:
  team: Core
  modules: 
    - :core:common
    - :core:data
    - :core:data-test
    - :core:database
    - :core:datastore
    - :core:datastore-test
    - :core:designsystem
    - :core:domain
    - :core:model
    - :core:network
    - :core:ui
    - :core:testing
    - :core:analytics
    - :sync:work
    - :sync:sync-test
tools:
  team: Tools
  modules:
    - :app-nia-catalog
    - :benchmarks
    - :lint
    - :ui-test-hilt-manifest
```

## Outputs

All plugin outputs can be found in the `build/untangler` directory.

| File  | Description |
| ------------- | ------------- |
| `analyzeModuleGraph.dot` | Full Dependency Graph |
| `analyzeModuleGraph-height.dot` | Dependency graph isolated to only include nodes that are part of the longest paths. |
| `analyzeModuleGraph-reduced.dot` | Transitively reduced graph. Removes information but can be helpful in understanding the structure of large dependency graphs. |
| `analyzeModuleGraph-adjacencyMatrix.txt` | Adjency Matrix that can be imported into other tools for further analysis. e.g. generating a co-occcurence matrix using pandas. |
| `changeFrequency.txt` | CSV for the calculated change frequency of each module. |


Graphs can be rendered using the `dot` command from [Graphviz](https://graphviz.org/)
```
$ dot -Tpng build/untangler/analyzeModuleGraph-height.dot -o analyzeModuleGraph-height.png
```

Alternatively, the dependency graph can be imported into a tool such as [Gephi](https://gephi.org/) for further analysis.


# References

* ["(Solid) Modularization - Untangling the dependency graph" - Siggi Jonsson @ DroidconSF 2022](https://www.droidcon.com/2022/06/28/solid-modularization-untangling-the-dependency-graph/) ([Slides](https://speakerdeck.com/siggijons/modularization-siggi-jonsson))
* [Module Graph Assert](https://github.com/jraska/modules-graph-assert)
* [Dependency Analysis Gradle Plugin](https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin)
* [Driving architectural improvements with dependency metrics - Karl Erliksson, John Kvarnefalk @ BazelCon 2022](https://www.youtube.com/watch?v=k4H20WxhbsA)

