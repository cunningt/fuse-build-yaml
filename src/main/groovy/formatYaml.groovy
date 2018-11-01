package com.redhat.fuse

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
//import org.jboss.prod.preProcessYaml
import java.nio.file.Paths
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.regex.Matcher
import groovy.util.logging.Slf4j
import java.net.URLDecoder
import java.net.URLEncoder
import groovy.json.JsonOutput.*
//GStringTemplateEngine
import groovy.text.*
import com.rits.cloning.Cloner
import java.net.URI
import org.apache.commons.lang3.text.WordUtils
import groovy.json.*

@Slf4j
class VersionManipulator 
{
    public static final Pattern OSGIPattern = Pattern.compile("(?<name>[\\w|-]*)((?<major>\\d+)\\.(?<minor>\\d+)\\.(?<micro>\\d+)){1}(?:[\\.|-])?(?<qualifier>.*)")
    //public static final Pattern redhatQualifierPattern = Pattern.compile("^[\w-]*-?redhat-(\d+)")

    protected String original
    public String name

    //Our version deciminated to ints
    public Integer major = 0
    public Integer minor = 0
    public Integer micro = 0
    public String qualifier

    VersionManipulator(Integer major, Integer minor, Integer micro, String qualifier)
    {
        log.info("Maj: $major Minor: $minor Micro: $micro Qualifier: $qualifier")
        this.major = major
        this.minor = minor
        this.micro = micro
        this.qualifier = qualifier
    }

    VersionManipulator(String v)
    {
        //Grab our likely version string
        log.debug("Looking for version in string $v")
        original = (String) v

        Matcher m = OSGIPattern.matcher(v)

        if (m.find())
        {
            for (int i=0; i < m.groupCount(); i++)
            {
                String mat = m.group(i)
                log.debug("Found $mat")
            }
            name = m.group("name")
            major = major.valueOf(m.group("major"))
            minor = minor.valueOf(m.group("minor"))
            micro = micro.valueOf(m.group("micro"))
            qualifier = m.group("qualifier")
        }
    }

    public String getOriginal()
    {
        return original
    }

    @Override
    public String toString()
    {
        return String.format("%s.%s", semverString(), qualifier)
    }

    public String semverString()
    {
        return String.format("%d.%d.%d", major, minor, micro)
    }

    public ArrayList<Integer> semverArray()
    {
        return ArrayList[major, minor, micro]
    }

    public Map<String, Integer> semverMap()
    {
        Map<String, Integer> semverMap = new HashMap<String, Integer>(
            "major":major,
            "minor":minor,
            "micro":micro);
    }

    public String swap(VersionManipulator other)
    {
        def o = this.original
        return o.replaceAll(semverString(), other.semverString())
    }

    /*
    public Integer compare(VersionManipulator other)
    {
        //-1 less than
        //0 match 
        //1 greater than
    }
    */
    /*
    VersionManipulator(VersionManipulator)
    {
    }
    */
}

/* BuildConfigSection yaml */
@Slf4j
class BuildConfigSection {

    private final ArrayList parsedSection
    private final String rawSection
    public ArrayList adjustedParsedSection 
    
    BuildConfigSection(String rawSection)
    {
        //Copy our section
        this.rawSection = rawSection
        def sectionHeader = (rawSection.split("\n")[0]).replace("- name:  ", "")
        
        log.info("Parsing build section: $sectionHeader")
        
        //Parse it
        def yml = new Yaml()
        parsedSection = yml.load(this.rawSection)

        //Make a cloned "working copy"
        def cloner = new Cloner()
        adjustedParsedSection = cloner.deepClone(parsedSection)
    }

    public String getOriginalAsString()
    {
        return rawSection
    }
    public ArrayList getOriginal()
    {
        return parsedSection
    }

    public ArrayList getAdjusted()
    {
        return adjustedParsedSection
    }

    public String decodeURLs(String encodedURL)
    {
        //We dont need to decode the non-encoded URIs 
        if (encodedURL.startsWith("git+ssh"))
            return encodedURL

        return URLDecoder.decode(encodedURL, "UTF-8");
    }

    public void commentsToArray()
    {
        adjustedParsedSection = new Yaml().load(commentsToString(rawSection))
    }

    public void recomment()
    {
        /*
        def inter =  parsedSection[0].intersect(adjustedParsedSection[0])
        log.debug("Recommenting section\n before:$adjustedParsedSection \nafter: $inter")
        adjustedParsedSection = new ArrayList()
        adjustedParsedSection.add(inter)*/

        /* I was sick in my mouth a little bit writing this but it works */
        def toRemove = new ArrayList()
        for(k in adjustedParsedSection[0].keySet())
        {
            if(!parsedSection[0].containsKey(k))
            {
                log.debug("missing $k, from $adjustedParsedSection removing")
                toRemove.add(k)
            }
        }
        for(k in toRemove)
        {
            adjustedParsedSection[0].remove(k)
        }
    }

    public String commentsToString(String raw)
    {
        def stripped = raw.replaceAll("([\\s]{4}#)", "  ")
        log.debug("Merging commented fields and reparsing...\n")
        log.debug("Unmerged:\n\t$rawSection Merged:\n\t$stripped")
        return stripped
    }

    public void decodedURL()
    {
        for ( a in adjustedParsedSection )
        {
            for (k in a.keySet())
            {
                if(java.lang.String == a[k].getClass())
                    a[k] = decodeURLs(a[k])
            }
        }
    }

    public String friendlyName()
    {
        def adj = getAdjusted()
        log.debug("Looking for version in $adj")
        def version = new VersionManipulator(adj[0]['name'])
        return WordUtils.capitalizeFully(version.name.replaceAll("-", " ").trim())
    }

    public void swapInternalAndExternalURL()
    {
        this.commentsToArray()
        this.decodedURL()

        def diff =  getAdjusted()[0] - getOriginal()[0]

        //This will be changing soon
        if(diff['pushScmUrl'])
        {
            adjustedParsedSection[0]['scmUrl'] = diff['pushScmUrl']
        }
    }

    public void adjustBuildConfigName()
    {
        def version = new VersionManipulator(getAdjusted()[0]['name'])
        def scmVersion = new VersionManipulator(getAdjusted()[0]['scmRevision'])
        if (!version.semverMap().equals(scmVersion.semverMap()))
        {
            log.info("Adjusting version in BC name as it differs from tag:")
            log.debug("version: " + (String)version.semverMap() + " scmVersion: " + (String)scmVersion.semverMap())
            def orig = version.getOriginal()
            def altered = version.swap(scmVersion)
            adjustedParsedSection[0]['name'] = altered
            log.info("\tBefore: $orig After: $altered")
        }
    }

    public boolean checkKeysPresent()
    {
        def neededkeys = ["name", "project", "scmUrl", "scmRevision"]
        def ret = false
        for(k in neededkeys)
        {
            ret = getAdjusted()[0].containsKey(k)
            if(!ret)
            {
                log.error("$k is missing!")
                return ret
            }
        }
        return ret
    }

    public void adjustProjectName()
    {
        def project = getAdjusted()[0]['project']
        def fixeduri = getAdjusted()[0]['scmUrl']
        /*
        This is a bit overly specific we need to figure out how to convert
        user@domain.com:somerepo/project to a valid URI (git+ssh)
        */
        if (fixeduri.startsWith("git@"))
        {
            fixeduri = fixeduri.replace("git@", "git+ssh://")
            fixeduri = fixeduri.replace(".com:", ".com/")
        }

        //Parse the URI and get path section (we could do this with regex but i'm lazy)
        def uri =  new URI(fixeduri)
        def path = uri.getPath()

        log.debug("URI: $uri, path:$path, project:$project")
        def (repo, proj) = project.split("/")
        //work around for fabric8io- instead of fabric8io/
        def splpath = path.split("/")
        def srepo, sproj
        if(splpath.size() > 2)
        {
            srepo = splpath[1]
            sproj = splpath[2]
        }
        else
        {
            srepo = (String) splpath[1].split("-")[0]
            sproj = (String) splpath[1].split("-")[1..-1].join("-")
        }
        sproj = sproj.replace(".git", "")
        if(proj.equals(sproj) && !srepo.equals(repo))
        {
            log.info("Adjusting project in BC as it differs from scmUrl:")
            repo = srepo
            project = repo + "/" + proj
            def orig = getAdjusted()[0]['project']
            log.info("\tBefore $orig After $project")
            adjustedParsedSection[0]['project'] = project
        }
        else if(!proj.equals(sproj))
        {
            log.error("$proj and $sproj do not match")
        }
    }

    public boolean hasDependency(BuildConfigSection other, boolean adjustedOnly)
    {
        boolean match = false
        for (dependency in adjustedParsedSection[0]['dependencies'])
        {
            if(!adjustedOnly)
            {
                if(other.getOriginal()[0]['name'].equals(dependency))
                    match = true
            }
            if(other.getAdjusted()[0]['name'].equals(dependency))
                match = true
        }
        return match
    }

    public void adjustDependencies(BuildConfigSection other)
    {
        def alteredDeps = []
        for (dependency in adjustedParsedSection[0]['dependencies'])
        {
            def otherOriginalName = other.getOriginal()[0]['name']
            def otherName = other.getAdjusted()[0]['name']
            if(!otherName.equals(otherOriginalName))
            {
                if(otherOriginalName.equals(dependency) && !otherName.equals(dependency))
                {
                    dependency = other.getAdjusted()[0]['name']
                    log.info("Adjusting dependency in ${adjustedParsedSection[0]['name']} from $otherOriginalName to $dependency")
                }
            }
            alteredDeps.add(dependency)
        }
        if(adjustedParsedSection[0].containsKey('dependencies'))
        {
            adjustedParsedSection[0]['dependencies'] = alteredDeps
        }
    }
}

/* Pre-parser and amalgimator */
@Slf4j
class BuildConfig {
    private String rawFileContents
    private Map<String, String> mavenProperties
    public Yaml parsedAmalgimatedYaml
    private def buildConfigs = []
    
    public BuildConfig(String filePath, Map<String> properties)
    {
        this.BuildConfig(new File(filePath), properties)
    }

    public BuildConfig(java.io.File file, Map<String> properties)
    {
        rawFileContents = file.getText('UTF-8')
        mavenProperties = properties

        DumperOptions options = new DumperOptions() 
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak())
        options.setSplitLines(true)
        options.setPrettyFlow(true)
        options.setWidth(30)
        parsedAmalgimatedYaml = new Yaml(options)
        this.preParse()
    }

    public String depGraph()
    {
        def templ = new GStringTemplateEngine()
        def source = '''
            <!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML//EN">
            <html> 
            <head>
            <title><%=product.name%> <%=product.version%> <%=product.stage%> Dep tree</title>
            <script type="text/javascript" src="http://visjs.org/dist/vis.js"></script>
            <link href="http://visjs.org/dist/vis.css" rel="stylesheet" type="text/css" />
            </head>

            <body>
            <h1><%=product.name%> <%=product.version%> <%=product.stage%> Dependencies</h1>

            <div id="deptree"></div>

            <script type="text/javascript">
            var components = new vis.DataSet([
            <% nodes.eachWithIndex { n, index -> %>
                { id:<%= n.id %>, label: '<%= n.friendlyname %>'}<% if(index != nodes.size()-1) out << ',' %> <%} %>
            ]);

            var dependencies = new vis.DataSet([
            <% edges.eachWithIndex { e, index -> %>
                { from:<%= e.from %>, to:<%= e.to %>, arrows: 'to'}<% if(index != edges.size()-1) out << ',' %> <%} %>
            ]);

            var container = document.getElementById('deptree');
            var data = {
               nodes: components,
               edges: dependencies
            };

            var options = {
            };

            var deptree = new vis.Network(container, data, options);
            </script>
            </body> </html>
            '''
        HashMap parsed = this.dumpAsObj(rawFileContents)
        def product = parsed['product']['name']
        def version = parsed['version']
        def stage = parsed['product']['stage']

        def nodes = [nodes:[],edges:[],product:[name:product, version:version, stage:stage]] //[id:0, friendlyname:"foo"], [id:1, friendlyname:"bar"]]]
        for( bc in buildConfigs )
        {
            nodes['nodes'] << [id: bc.hashCode(), friendlyname:bc.friendlyName()]
            for( bcs in buildConfigs )
            {
                if(bc.hasDependency(bcs, true))
                nodes['edges'] << [from:bc.hashCode(), to: bcs.hashCode()]
            }
        }
        //println new JsonBuilder( nodes ).toPrettyString()
        ///print nodes
        return templ.createTemplate(source).make(nodes).toString()
    }

    private ArrayList preParse(String raw)
    {
        //Skip Ahead in string to "builds:"
        def spl = raw.split("builds:")

        //Dont try and parse extras
        Pattern extraPattern = Pattern.compile("(outputPrefixes:.*)")
        def stripExtra = extraPattern.split(spl[1])

        //Read each " - name: " section (grab with newline delim)"
        Pattern sectionPattern = Pattern.compile("(?<!#)(- name:)")
        def splPattern = sectionPattern.split(stripExtra[0])

        def bc = []
        for ( p in splPattern )
        {
            if(p.length() > 2)
            {
                log.debug("Preparsing BC sections")
                log.debug("\n\n\n" + "-------START------\n" + "- name: $p" + "########END#######\n")
                def section = new BuildConfigSection("- name: "+p)
                bc.add(section)
            }
        }
        return bc
    }
    
    private void preParse()
    {
        def buildsections = this.preParse(rawFileContents)
        for(section in buildsections)
        {
            //Sanity check
            assert section.checkKeysPresent()
            //Use github (upstream/midstream)
        //section.swapInternalAndExternalURL()
            //Change the BC name to match the scm tag ver
            section.adjustBuildConfigName()
            //Change the project section to match repo location
            section.adjustProjectName()
            section.recomment()
        }
        //Re-target dependencies
        log.info("Readjusting dependencies")
        for (bcs in buildsections)
        {
            for (bcsr in buildsections)
            {
                bcs.adjustDependencies(bcsr)
            }
        }
        buildConfigs = buildsections
    }
    
    public HashMap dumpAsObj(String fileAsString)
    {
        return parsedAmalgimatedYaml.load(fileAsString)
    }

    public String dump()
    {
        //Load our original
        def parsed = this.dumpAsObj(rawFileContents)
        //clear the old builds
        parsed['builds'] = []
        for (b in buildConfigs)
        {
            parsed['builds'].add(b.getAdjusted()[0])
        }
        //def templ = new SimpleTemplateEngine()
        //print parsedAmalgimatedYaml.dump(parsed)
        
        //Make it a little bit more pretty
        String ret = parsedAmalgimatedYaml.dump(parsed)

        def spl = ret.split("builds:")
        def pretty = spl[0] + "\nbuilds:\n"
        for(b in preParse(ret))
        {
            pretty = pretty + "\n" + b.getOriginalAsString() + "\n"
        }
        return pretty
    }

}


//List all properties
//project.getProperties().each { k, v -> println "${k}:${v}" }

//org.apache.maven.project.MavenProject project
def yamlpath = Paths.get(project.getBuild().getDirectory(), "extra-resources", "fusefis.yaml").toString()
log.info("Attempting to load file $yamlpath") 
def rawYamlFileH = new File(yamlpath)

//Load our YAML file and fix it
BuildConfig bc = new BuildConfig(rawYamlFileH, project.getProperties())

//Print the fixed yaml to term (we can add a write back funct at some point)
print bc.dump()

//Write and print the depgraph to the target dir, we need to add it to the POM to deploy it (todo)
def depgraphoutputf = new File(Paths.get(project.getBuild().getDirectory(), "extra-resources", "Deptree.html").toString())
depgraphoutputf << bc.depGraph()
print bc.depGraph()

