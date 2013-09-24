# frereth-renderer

I keep running into brick walls trying to get a Common Lisp
version working. Try it with clojure instead.

## Installation

Clone this repository for github. Update your project.clj and copy
the src directory from this one into yours.

(Hopefully, the next 2 paragraphs are obsolete, using kephale's
lwjgl distribution)

Get a copy of the lwjgl binaries (most likely in a .jar file). Unzip
them (it's really just a .zip) inside target/native.

Note that that isn't what I actually have at all: I have a native
directory with a subdirectory for each supported platform. Which
means this part of the documentation is a lie. 

Run lein git-deps to clone the other repositories frereth-renderer
depends on.

cd into ./lein-git-deps/penumbra. Run `lein javac`

That should build some .class files under target/classes/penumbra.
Copy that directory into the frereth-renderer/target/classes folder.

Now you should be ready to run.

No, it ain't pretty. But it's a start.

## Usage

Beats me. I'm making this up as I go.

    $ java -jar frereth-renderer-0.1.0-standalone.jar [args]

## License

Copyright © 2013 James Gatannah

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
