
struct Pass
{
	char name;
	int id; 
};



int main() 
{
	struct Pass pass1;
	pass1.name = 'A';
	pass1.id = 1;
	struct Pass pass2;
	pass2.name = pass1.name;
  	if (pass2.name == 'A') {
    		ERROR: return;
  	}
	return 0;
}


