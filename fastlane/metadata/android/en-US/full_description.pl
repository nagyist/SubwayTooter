#!/usr/bin/perl --
use strict;
use warnings;
use utf8;
use feature qw(say);

my $file = $0;
$file =~ s/\.pl$/\.txt/;

local $/=undef;
my $text = <DATA>;

$text =~ s/[\x00-\x20]+/ /g;
$text =~ s/\A //;
$text =~ s/ \z//;
$text =~ s/> />/g;
$text =~ s/ </</g;

open(my $fh,">:utf8",$file) or die "$file $!";
say $fh $text;
close($fh) or die "$file $!";


__DATA__

<p>Mastodon client for Android 8.0 or later.</p>

<p>Also this app has partially support for Misskey. But it does not include function to use message, drive, reversi, widget.</p>

<p># Multiple accounts, Multiple columns:</p>
<ul>
<li>You can swipe horizontally to switch columns and accounts.</li>
<li>You can add, remove, rearrange columns.</li>
<li>Column types: home, notification, local-TL, federate-TL, search, hash tags, conversation, profile, muted, blocked, follow requests, etc.</li>
</ul>

<p># Cross account action:</p>
<ul>
<li>You can favorite/follow operation as a user different from bind to column.</li>
</ul>

<p># Other information:</p>
<ul>
<li>source code is here. <a href="https://github.com/tateisu/SubwayTooter">https://github.com/tateisu/SubwayTooter</a></li>
<li>Some of the icons used in this app is based on the Icons8. <a href="https://icons8.com/license/">https://icons8.com/license/</a></li>
</ul>
