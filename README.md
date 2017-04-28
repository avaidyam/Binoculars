# Binoculars
Distributed Heterogeneous Cluster Computing in Computational Proteomics

---

## Instructions
1. `git clone <url>` -- Clone the repository to your local system.
1. `cd Binoculars/`
1. `nano build.properties` -- Add the properties described in `build.xml` (`jdk.home.1.8` for JDK, and `artifact.class` for running the project).
1. `ant build` -- Build the framework along with PFP, LZerD, or PL-PatchSurfer.
1. `ant run` -- Run the framework and project.
  
Please keep in mind that while functional, without the `UnifiedServices` directory placed appropriately and the PHP script running, you must use the JS console to interact with the system. There is no preconfigured deployment script available yet for the `UnifiedServices` package. 

For a comprehensive explanation of this system, please see the **Wiki** for the manuscript draft and presentation. This is a work in progress, and the **Issues** tracker will contain pending modifications. Please report bugs!

## Contributor Guide
[CONTRIBUTING.md](CONTRIBUTING.md)

## Contributions
[CONTRIBUTORS.md](CONTRIBUTORS.md)

## License 
[LICENSE.md](LICENSE.md)
