# protean-cli

Command line interface for Protean.


## Installation

[Download](https://github.com/passivsystems/protean-cli/releases/download/0.4.0/protean-cli-0.4.0.tgz) the release.
Unzip/extract tar and place the two files in the same location on your path somewhere.

## Usage

    java -jar protean.jar

or

    protean

will show usage instructions.

    protean services

or

    java -jar protean.jar services

will list configured services.

We recommend 'drip' (github.com/flatland/drip) to speed things up with the command line.  The sample script uses drip.


## Build

    lein uberjar

if you want to build from source 


## License

Protean CLI is licensed with Apache License v2.0.
