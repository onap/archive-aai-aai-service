#!/usr/bin/perl

use strict;

use Getopt::Std;

my %opts = ();

getopts("n:", \%opts);

my $namespace = '';
if (!$opts{'n'}) { 
	$namespace = "http://org.openecomp.aai.inventory";
} else {
	$namespace = $opts{'n'};
}

my $has_xml_root_element = 0;
my $has_a_class = 0;
my @file = ();

my $added_rel = 0;

while (<>) { 
    1 while chomp;
    # we're going to read it again
    
    my $line = $_;
    $line =~ s/(protected boolean (inMaint|isClosedLoopDisabled|isBoundToVpn|dhcpEnabled))(\;)/$1 \= false\;/i;
 
    push @file, $line;
    
    #if ($_ =~ /^import/ && $added_rel == 0) { 
    # push @file, "import org.openecomp.aai.domain.yang.rel.*;";
    # $added_rel = 1;
    #}
    if ($_ =~ /^\@XmlRootElement/) { 
	$has_xml_root_element = 1;
    }
    if ($_ =~ /^public\sclass\s(\S+)\s{/) { 
	$has_a_class = 1;
    }
}

if ($has_xml_root_element == 0 && $has_a_class == 1) {
    my $printed_include = 0;
    foreach my $line (@file) { 
	if ($line =~ /^import/ && $printed_include == 0) { 
	    print "import javax.xml.bind.annotation.XmlRootElement;\n";
	   
	    $printed_include++;
	}
	if ($line =~ /^public\sclass\s(\S+)\s{/) { 
	    my $className = $1;
	    my @parts = $line =~ /([A-Z](?:[A-Z0-9]*(?=$|[A-Z0-9][a-z])|[a-z0-9]*))/g;
	    print "\@XmlRootElement(name = \"" . lc join('-', @parts) . "\", namespace = \"$namespace\")\n";
	}
	print "$line\n";
    }
} else { 
    foreach my $line (@file) { 
	print "$line\n";
    }
}
