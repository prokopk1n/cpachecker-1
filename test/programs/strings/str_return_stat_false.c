char str[] = "Hello";
int main() {
  if (f(str) == 'H') {
    ERROR: return;
  }
  return 0;
}

char f(char* str) {
 if(str) 
	return str[0];

 return 'a';
}
